#version 100

precision mediump float;

uniform sampler2D uTexSampler;
uniform float uDisplayAspectRatio;
varying vec2 vTexSamplingCoord;

void main() {
  vec4 source = texture2D(uTexSampler, vTexSamplingCoord);

  // Fixed scanlines are intentionally subtle and do not require a second texture or blur pass.
  float scanline = 0.955 + 0.045 * sin(vTexSamplingCoord.y * 1080.0 * 3.14159265);

  // Use the display aspect ratio so the soft vignette remains circular on widescreen video.
  vec2 centered = vTexSamplingCoord - vec2(0.5);
  centered.x *= uDisplayAspectRatio;
  float vignette = 1.0 - 0.10 * smoothstep(0.32, 0.74, dot(centered, centered));

  gl_FragColor = vec4(source.rgb * scanline * vignette, source.a);
}
