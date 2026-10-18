# MRI viewer slider review

Reviewed in the Android emulator using the local **BrainChop demo sample, not a personal scan**.

## Updated layout

The viewer keeps axial, coronal, and sagittal choices in one compact, one-tap tab row. The duplicate plane button and slide-out plane panel are removed. The slice control no longer sits in a large outlined card: the slice count and coordinate share a slim header, the track and handle are compact, and small previous/next buttons flank the slider. Those buttons retain 48 dp touch targets, and the slider announces its slice and coordinate to accessibility services.

The viewer uses one unframed deep-navy canvas. A faint cyan/blue perimeter glow fades inward across the screen instead of forming separate top and bottom bands. The MRI viewport is transparent, and near-black rendered pixels blend with the canvas so the slice image does not leave a rectangular edge; source voxel data is unchanged. The initial image scale is 130% of fit to give the brain more of the available display.

Plane and slice controls remain easy to reach. Plane labels are slightly larger and sit a little higher; lower actions are slightly higher and spaced apart, while the slice slider stays in place. Slice buttons keep 48 dp touch targets. The initial image scale is now 135% of fit. Orientation markers start hidden; the Home Settings sheet has a saved “Orientation markers” switch for R/L and A/P labels.

![Updated MRI viewer with a continuous deep-blue canvas, subtle perimeter glow, larger brain, grouped controls, and Focus at the lower right](04-compact-viewer.png)

The loading state now carries NeuroView branding, with the app logo, an accent progress ring, and a short status message over the same blue glow treatment.

![Branded MRI loading screen](07-branded-loading.png)

## Focus and probe controls

Focus now sits at the lower-right end of the normal control row. In Focus mode, an “Exit focus” control stays at the lower-right of the image area. Probe Crosshair lives in Display adjustments; enabling it closes the sheet and arms point picking. The image first shows a short prompt, then a tap places the crosshair and opens a reading card with voxel indices and intensity. The cross-plane reference follows the selected point across intersecting views. Switching the setting off clears the reading and crosshair.

![Probe Crosshair switch in Display adjustments](08-probe-crosshair-setting.png)

![Probe Crosshair armed with the tap instruction](09-probe-crosshair-armed.png)

![Probe Crosshair reading card with voxel coordinates and intensity](10-probe-crosshair-reading.png)

![Home Settings with Orientation markers off by default](05-orientation-markers-setting-off.png)

![Same MRI viewer after enabling orientation markers in Settings](06-orientation-markers-enabled.png)

## Slider observations

The midpoint at 129/256 shows anatomy. The first and last positions in this demo show black panels, so the screenshots capture the volume boundaries as well as the useful midpoint. Those blank edges are a property of this sample and its coverage; the viewer keeps the original slice range intact instead of silently skipping positions.

![Original viewer at the midpoint, before the compact-control change](01-midpoint-slice.png)

![Demo at slice 1 of 256](02-first-slice.png)

![Demo at slice 256 of 256](03-last-slice.png)

## Orientation labels

For axial and coronal views, **R on screen-left and L on screen-right is the radiological convention**: it shows the patient as if viewed face-to-face. Neurological display convention places the patient’s left on screen-left instead, so viewers can differ. The radiological convention is a normal display choice, not by itself evidence that the source scan was stored or altered as a mirror image. [FMRIB’s MRI primer describes the radiological and neurological conventions](https://www.fmrib.ox.ac.uk/primers/appendices/anatomy.pdf).

NeuroView’s MPR renderer samples the volume through its NIfTI RAS+ affine and deliberately maps patient-right to the left side of the axial/coronal display ([renderer](../../../app/src/main/java/com/marcusfunt/neuroview/mri/MprRenderer.kt)). That reorients the displayed view; it does not rewrite the source voxel data. The labels still depend on correct orientation metadata. NeuroView already warns when orientation metadata is unavailable, but a present affine cannot prove that the source metadata itself is correct.

## Suggestions

- Keep the plane tabs and slice readout visible in the normal viewer; the Focus mode remains available for a larger image-only view.
- Keep compact graphics paired with forgiving touch areas and explicit accessibility labels.
- Keep the marker preference independent from the image orientation; show or hide labels without flipping or changing the scan.
- If black boundary slices are confusing in future samples, consider a small “outside acquired coverage” hint only after the renderer can distinguish no sampled voxels from measured black data.
