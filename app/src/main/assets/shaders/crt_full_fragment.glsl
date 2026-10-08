#version 100

precision mediump float;

uniform sampler2D uTexSampler;
uniform float uSourceAspectRatio;
varying vec2 vTexSamplingCoord;

void main() {
  vec2 screen = vTexSamplingCoord * 2.0 - 1.0;

  // Barrel distortion bows the picture outward. Coordinates outside the source become black.
  vec2 warped = screen;
  warped.x *= uSourceAspectRatio;
  warped *= 1.0 + 0.115 * dot(warped, warped);
  warped.x /= uSourceAspectRatio;
  vec2 sourceUv = warped * 0.5 + 0.5;
  if (sourceUv.x < 0.0 || sourceUv.x > 1.0 || sourceUv.y < 0.0 || sourceUv.y > 1.0) {
    gl_FragColor = vec4(0.0);
    return;
  }

  vec4 source = texture2D(uTexSampler, sourceUv);
  float scanline = 0.91 + 0.09 * sin(sourceUv.y * 480.0 * 3.14159265);

  // A superellipse keeps the middle of each edge visible while rounding the corners.
  float roundedCorner = pow(abs(screen.x), 4.0) + pow(abs(screen.y), 4.0);
  float screenMask = 1.0 - smoothstep(1.72, 2.0, roundedCorner);
  float vignette = 1.0 - 0.15 * smoothstep(0.35, 1.0, dot(screen, screen));

  gl_FragColor = vec4(source.rgb * scanline * vignette * screenMask, source.a * screenMask);
}
