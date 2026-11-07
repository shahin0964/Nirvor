package com.nirvor.app.ui.tools.gametools.engine

import android.opengl.GLES20
import android.util.Log

object ShaderUtils {
    private const val TAG = "ShaderUtils"

    const val VERTEX_SHADER_LIGHTING = """
        uniform mat4 uMVPMatrix;
        uniform mat4 uMVMatrix;
        uniform vec3 uLightPos;
        attribute vec4 aPosition;
        attribute vec3 aNormal;
        attribute vec4 aColor;
        varying vec4 vColor;
        varying float vDiffuse;
        varying float vSpecular;

        void main() {
            vec3 modelViewVertex = vec3(uMVMatrix * aPosition);
            vec3 modelViewNormal = normalize(vec3(uMVMatrix * vec4(aNormal, 0.0)));
            vec3 lightVector = normalize(uLightPos - modelViewVertex);
            float diffuse = max(dot(modelViewNormal, lightVector), 0.15);
            vec3 viewVector = normalize(-modelViewVertex);
            vec3 reflectVector = reflect(-lightVector, modelViewNormal);
            float specular = pow(max(dot(reflectVector, viewVector), 0.0), 16.0);

            vColor = aColor;
            vDiffuse = diffuse;
            vSpecular = specular * 0.35;
            gl_Position = uMVPMatrix * aPosition;
        }
    """

    const val FRAGMENT_SHADER_LIGHTING = """
        precision mediump float;
        varying vec4 vColor;
        varying float vDiffuse;
        varying float vSpecular;

        void main() {
            vec3 ambient = vColor.rgb * 0.35;
            vec3 diffuse = vColor.rgb * vDiffuse * 0.65;
            vec3 specular = vec3(1.0, 1.0, 1.0) * vSpecular;
            gl_FragColor = vec4(ambient + diffuse + specular, vColor.a);
        }
    """

    fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) return 0

        var program = GLES20.glCreateProgram()
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program: " + GLES20.glGetProgramInfoLog(program))
                GLES20.glDeleteProgram(program)
                program = 0
            }
        }
        return program
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader != 0) {
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader $type: " + GLES20.glGetShaderInfoLog(shader))
                GLES20.glDeleteShader(shader)
                return 0
            }
        }
        return shader
    }
}
