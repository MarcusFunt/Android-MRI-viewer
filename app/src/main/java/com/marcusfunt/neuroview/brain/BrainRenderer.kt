package com.marcusfunt.neuroview.brain

import android.content.Context
import android.util.Log
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLES2 renderer for the locally bundled, optimized Brainchop demonstration
 * cortex mesh. No network access or GLB runtime needed. The GPU consumes
 * the NVB1 vertex and index buffers natively (under GLES2's 65k vertex cap).
 * Rendering is driven by GLSurfaceView.RENDERMODE_WHEN_DIRTY.
 */
internal class BrainRenderer(private val context: Context) : GLSurfaceView.Renderer {
    @Volatile var yawDegrees = -85f
    @Volatile var pitchDegrees = -12f

    private data class GpuMesh(
        val vertices: FloatBuffer,
        val triangles: ShortBuffer,
        val triangleCount: Int,
        val color: FloatArray,
    )

    private var program = 0
    private var positionHandle = -1
    private var normalHandle = -1
    private var mvpHandle = -1
    private var modelViewHandle = -1
    private var tintHandle = -1
    private var meshes = emptyList<GpuMesh>()
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val modelView = FloatArray(16)
    private val mvp = FloatArray(16)
    private var frames = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i("NeuroViewBrain", "GLES2 surface created")
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
        modelViewHandle = GLES20.glGetUniformLocation(program, "uModelView")
        tintHandle = GLES20.glGetUniformLocation(program, "uTint")
        val cpuMeshes = runCatching {
            listOf(BrainAssetLoader.load(context.assets.open(BrainAssetLoader.ASSET_NAME)))
        }.getOrElse { failure ->
            Log.e("NeuroViewBrain", "Bundled model unavailable; using offline fallback", failure)
            BrainGeometry.build()
        }
        Log.i("NeuroViewBrain", "CPU meshes: ${cpuMeshes.size}")
        meshes = cpuMeshes.map { cpu ->
            val vertexData = ByteBuffer.allocateDirect(cpu.vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer().apply {
                    put(cpu.vertices)
                    position(0)
                }
            val indexData = ByteBuffer.allocateDirect(cpu.indices.size * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer().apply {
                    put(cpu.indices)
                    position(0)
                }
            GpuMesh(vertexData, indexData, cpu.indices.size, BRAIN_TINT.copyOf())
        }
        Matrix.setLookAtM(view, 0, 0f, 0f, 5.25f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.i("NeuroViewBrain", "GL viewport ${width} x ${height}")
        GLES20.glViewport(0, 0, width, height)
        val safeAspect = width.toFloat() / height.coerceAtLeast(1)
        Matrix.perspectiveM(projection, 0, 36f, safeAspect, 0.1f, 20f)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (frames++ == 0) Log.i("NeuroViewBrain", "First GL draw, program=$program, meshes=${meshes.size}")
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (program == 0) return

        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, yawDegrees, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, pitchDegrees, 1f, 0f, 0f)
        // Match the original GLB preview: NVBM is mirrored in X/Z relative to the\n        // prototype camera basis. A 180-degree Y rotation restores that basis.
        Matrix.rotateM(model, 0, 180f, 0f, 1f, 0f)
        Matrix.multiplyMM(modelView, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, modelView, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(modelViewHandle, 1, false, modelView, 0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(normalHandle)

        meshes.forEach { mesh ->
            GLES20.glUniform3fv(tintHandle, 1, mesh.color, 0)
            mesh.vertices.position(0)
            GLES20.glVertexAttribPointer(
                positionHandle, 3, GLES20.GL_FLOAT, false, 6 * 4, mesh.vertices,
            )
            mesh.vertices.position(3)
            GLES20.glVertexAttribPointer(
                normalHandle, 3, GLES20.GL_FLOAT, false, 6 * 4, mesh.vertices,
            )
            mesh.triangles.position(0)
            GLES20.glDrawElements(
                GLES20.GL_TRIANGLES, mesh.triangleCount,
                GLES20.GL_UNSIGNED_SHORT, mesh.triangles,
            )
        }
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
        if (frames == 1) Log.i("NeuroViewBrain", "GL frame status ${GLES20.glGetError()}")
    }

    private fun createProgram(vertex: String, fragment: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertex)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment)
        val linked = GLES20.glCreateProgram()
        GLES20.glAttachShader(linked, vertexShader)
        GLES20.glAttachShader(linked, fragmentShader)
        GLES20.glLinkProgram(linked)
        val result = IntArray(1)
        GLES20.glGetProgramiv(linked, GLES20.GL_LINK_STATUS, result, 0)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        check(result[0] != 0) { "Failed to link brain shader: ${GLES20.glGetProgramInfoLog(linked)}" }
        return linked
    }

    private fun compileShader(kind: Int, shaderSource: String): Int {
        val shader = GLES20.glCreateShader(kind)
        GLES20.glShaderSource(shader, shaderSource)
        GLES20.glCompileShader(shader)
        val result = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, result, 0)
        check(result[0] != 0) { "Failed to compile brain shader: ${GLES20.glGetShaderInfoLog(shader)}" }
        return shader
    }

    private companion object {
        private val BRAIN_TINT = floatArrayOf(0.79f, 0.49f, 0.45f)

        const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform mat4 uModelView;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            varying vec3 vEyePosition;
            void main() {
                vec4 eye = uModelView * vec4(aPosition, 1.0);
                vEyePosition = eye.xyz;
                vNormal = mat3(uModelView) * aNormal;
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """
        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec3 uTint;
            varying vec3 vNormal;
            varying vec3 vEyePosition;
            void main() {
                vec3 normal = normalize(vNormal);
                vec3 towardEye = normalize(-vEyePosition);
                if (dot(normal, towardEye) < 0.0) normal = -normal;
                // Warm tissue base with two soft studio spotlights.
                vec3 keyPosition = vec3(-2.6, 3.2, -1.1);
                vec3 keyDelta = keyPosition - vEyePosition;
                float keyDistance = length(keyDelta);
                vec3 keyDirection = keyDelta / max(keyDistance, 0.001);
                float keyCone = smoothstep(
                    0.62, 0.84,
                    dot(-keyDirection, normalize(vec3(0.43, -0.54, -0.72)))
                );
                float keyAttenuation = 1.0 / (1.0 + 0.035 * keyDistance * keyDistance);
                float keyWrap = max((dot(normal, keyDirection) + 0.24) / 1.24, 0.0);
                float keySpecular = pow(
                    max(dot(normal, normalize(keyDirection + towardEye)), 0.0), 34.0
                );

                vec3 fillPosition = vec3(2.8, 2.2, -0.7);
                vec3 fillDelta = fillPosition - vEyePosition;
                float fillDistance = length(fillDelta);
                vec3 fillDirection = fillDelta / max(fillDistance, 0.001);
                float fillCone = smoothstep(
                    0.62, 0.84,
                    dot(-fillDirection, normalize(vec3(-0.52, -0.41, -0.75)))
                );
                float fillAttenuation = 1.0 / (1.0 + 0.035 * fillDistance * fillDistance);
                float fillWrap = max((dot(normal, fillDirection) + 0.30) / 1.30, 0.0);
                float fillSpecular = pow(
                    max(dot(normal, normalize(fillDirection + towardEye)), 0.0), 28.0
                );

                float hemisphere = 0.5 + 0.5 * normal.y;
                float rim = pow(1.0 - max(dot(normal, towardEye), 0.0), 2.5);
                vec3 color = uTint * (0.28 + 0.10 * hemisphere);
                color += uTint * (0.78 * keyWrap * keyCone * keyAttenuation);
                color += uTint * (0.34 * fillWrap * fillCone * fillAttenuation);
                color += vec3(1.00, 0.73, 0.59) *
                    (0.20 * keySpecular * keyCone * keyAttenuation);
                color += vec3(0.55, 0.82, 1.00) *
                    (0.13 * fillSpecular * fillCone * fillAttenuation);
                color += vec3(0.20, 0.48, 0.62) * (0.11 * rim);
                gl_FragColor = vec4(min(color, vec3(1.0)), 1.0);
            }
        """
    }
}
