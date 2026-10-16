#!/usr/bin/env python3
"""Independent reference conversion QA using nibabel and pydicom.

Only deterministic SYNTHETIC volumes. Do not copy personal medical imagery
into the repository or publicly uploaded GitHub Actions artifacts.

Usage:
    python scripts/qa/reference_interop.py generate build/reference-qa
    NEUROVIEW_REFERENCE_FIXTURE_DIR=<absolute-dir> ./gradlew :app:testDebugUnitTest
    python scripts/qa/reference_interop.py verify build/reference-qa
"""
import argparse
import io
import json
import math
from pathlib import Path
import sys
from zipfile import ZipFile

import nibabel as nib
import numpy as np
import pydicom
from pydicom.dataset import Dataset, FileDataset, FileMetaDataset
from pydicom.uid import ExplicitVRLittleEndian, MRImageStorage, generate_uid


def expected_ras():
    # In-plane basis vectors perpendicular; out-of-plane step has a tilt.
    return np.array(
        [[0.0, -1.5, 0.2, 12.0],
         [1.2, 0.0, 0.0, -8.0],
         [0.0, 0.1, 2.0, 20.0],
         [0.0, 0.0, 0.0, 1.0]], dtype=np.float64
    )


def make_nifti(folder):
    # Intentionally asymmetric intensity pattern exposes flips, transposes
    # and incorrect voxel memory order.
    x, y, z = np.meshgrid(
        np.arange(7), np.arange(6), np.arange(5), indexing="ij"
    )
    voxels = (x * 17.0 + y * 3.5 + z * 0.75 - 50.0).astype(np.float32)
    image = nib.Nifti1Image(voxels, expected_ras())
    image.set_sform(expected_ras(), code=1)
    image.set_qform(np.eye(4), code=0)
    image.header.set_xyzt_units("mm")
    nib.save(image, str(folder / "reference_oblique.nii.gz"))
    (folder / "reference_expected.json").write_text(
        json.dumps({"shape": [7, 6, 5], "affine": expected_ras().tolist()}),
        encoding="utf-8",
    )


def make_dicom(folder):
    # DICOM LPS: columns advance +L, rows advance +S, normal points -P.
    # Coordinates are intentionally asymmetric. Source is native Explicit VR LE.
    source = folder / "dicom_input"
    source.mkdir(parents=True, exist_ok=True)
    study = generate_uid()
    series = generate_uid()
    for k in (4, 0, 2, 1, 3):  # deliberately non-spatial file order
        filemeta = FileMetaDataset()
        filemeta.MediaStorageSOPClassUID = MRImageStorage
        filemeta.MediaStorageSOPInstanceUID = generate_uid()
        filemeta.TransferSyntaxUID = ExplicitVRLittleEndian
        filemeta.ImplementationClassUID = generate_uid()
        ds = FileDataset(
            str(source / f"img_{k:02d}.dcm"), {}, file_meta=filemeta,
            preamble=b"\0" * 128,
        )
        ds.SOPClassUID = filemeta.MediaStorageSOPClassUID
        ds.SOPInstanceUID = filemeta.MediaStorageSOPInstanceUID
        ds.StudyInstanceUID = study
        ds.SeriesInstanceUID = series
        ds.Modality = "MR"
        ds.Rows = 6
        ds.Columns = 7
        ds.SamplesPerPixel = 1
        ds.PhotometricInterpretation = "MONOCHROME2"
        ds.BitsAllocated = 16
        ds.BitsStored = 16
        ds.HighBit = 15
        ds.PixelRepresentation = 1
        ds.PixelSpacing = [1.4, 1.1]
        ds.SliceThickness = "2.2"
        ds.SpacingBetweenSlices = "2.2"
        ds.ImageOrientationPatient = [1, 0, 0, 0, 0, 1]
        ds.ImagePositionPatient = [20, -35 - 2.2 * k, 5]
        ds.InstanceNumber = k + 1
        ds.RescaleSlope = "1.5"
        ds.RescaleIntercept = "-40"
        # column-fastest pixel order; signed values intentionally include negatives.
        raw = np.array([
            [k * 45 + y * 7 + x - 100 for x in range(7)]
            for y in range(6)
        ], dtype="<i2")
        ds.PixelData = raw.tobytes(order="C")
        ds.save_as(str(source / f"img_{k:02d}.dcm"), enforce_file_format=True)


def generate(folder):
    folder.mkdir(parents=True, exist_ok=True)
    make_nifti(folder)
    make_dicom(folder)
    print("Synthetic reference fixtures generated.")


def verify_nifti(folder):
    path = folder / "android_from_reference_dicom.nii.gz"
    assert path.exists(), f"Android conversion did not write {path}"
    im = nib.load(str(path))
    np.testing.assert_equal(im.shape, (7, 6, 5))
    # DICOM LPS column vector +L => RAS -R, row +S => +S,
    # slice normal -P => +A in RAS.
    expected = np.array([
        [-1.1, 0, 0, -20],
        [0, 0, 2.2, 35],
        [0, 1.4, 0, 5],
        [0, 0, 0, 1],
    ], dtype=np.float64)
    np.testing.assert_allclose(im.affine, expected, atol=1e-4, rtol=0)
    for k in range(5):
        for y in range(6):
            for x in range(7):
                raw = k * 45 + y * 7 + x - 100
                actual = float(np.asanyarray(im.dataobj)[x, y, k])
                assert abs(actual - (raw * 1.5 - 40.0)) < 1e-4, (x, y, k, actual)


def verify_dicom(folder):
    zip_path = folder / "android_from_reference_nifti.zip"
    assert zip_path.exists(), f"Android conversion did not write {zip_path}"
    with ZipFile(zip_path, "r") as container:
        files = sorted(container.namelist())
        assert len(files) == 5, files
        studies, series, sop = set(), set(), set()
        for k, filename in enumerate(files):
            assert filename.startswith("DERIVED_") and filename.endswith(".dcm")
            ds = pydicom.dcmread(io.BytesIO(container.read(filename)))
            assert str(ds.SOPClassUID) == "1.2.840.10008.5.1.4.1.1.7"
            assert "DERIVED" in ds.ImageType and "SECONDARY" in ds.ImageType
            assert ds.Rows == 6 and ds.Columns == 7
            assert ds.file_meta.TransferSyntaxUID == ExplicitVRLittleEndian
            assert not str(ds.PatientID or "") and not str(ds.PatientName or "")
            studies.add(str(ds.StudyInstanceUID))
            series.add(str(ds.SeriesInstanceUID))
            sop.add(str(ds.SOPInstanceUID))
            # Source NIfTI RAS -> DICOM LPS orientation and positions.
            np.testing.assert_allclose(
                [float(v) for v in ds.ImageOrientationPatient],
                [0, -1, 0, 1.5 / math.hypot(1.5, 0.1), 0, 0.1 / math.hypot(1.5, 0.1)],
                atol=0.07, rtol=0,
            )
            expected_pos = expected_ras() @ np.array([0, 0, k, 1])
            expected_pos[:2] *= -1
            np.testing.assert_allclose(
                [float(v) for v in ds.ImagePositionPatient],
                expected_pos[:3], atol=1e-4, rtol=0,
            )
            values = ds.pixel_array.astype(np.float64) * float(ds.RescaleSlope) + float(
                ds.RescaleIntercept
            )
            for y in range(6):
                for x in range(7):
                    expected_value = x * 17 + y * 3.5 + k * 0.75 - 50
                    assert abs(values[y, x] - expected_value) < 0.003, (
                        k, x, y, values[y, x], expected_value
                    )
        assert len(studies) == len(series) == 1
        assert len(sop) == 5


def verify(folder):
    verify_nifti(folder)
    verify_dicom(folder)
    print("PASS: nibabel NIfTI affine/intensities, pydicom geometry/UIDs/pixels.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("generate", "verify"))
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    (generate if args.action == "generate" else verify)(args.directory)


if __name__ == "__main__":
    main()
