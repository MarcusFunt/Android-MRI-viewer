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

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
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
            GpuMesh(vertexData, indexData, cpu.indices.size, cpu.color)
        }
        Matrix.setLookAtM(view, 0, 0f, 0f, 4.85f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val safeAspect = width.toFloat() / height.coerceAtLeast(1)
        Matrix.perspectiveM(projection, 0, 36f, safeAspect, 0.1f, 20f)
    }

    override fun onDrawFrame(gl: GL10?) {
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
                // Neutral studio key plus restrained cool bounce, not an emissive halo.
                vec3 keyLight = normalize(vec3(-0.48, 0.80, 0.52));
                vec3 fillLight = normalize(vec3(0.72, -0.24, 0.43));
                float key = max(dot(normal, keyLight), 0.0);
                float fill = max(dot(normal, fillLight), 0.0);
                float rim = pow(1.0 - max(dot(normal, towardEye), 0.0), 3.2);
                vec3 reflected = reflect(-keyLight, normal);
                float specular = pow(max(dot(reflected, towardEye), 0.0), 38.0);
                vec3 color = uTint * (0.34 + 0.62 * key + 0.18 * fill);
                color += vec3(0.18, 0.48, 0.70) * (0.13 * rim + 0.08 * fill);
                color += vec3(0.91, 0.96, 1.00) * (specular * 0.08);
                gl_FragColor = vec4(min(color, vec3(1.0)), 1.0);
            }
        """
    }
}
