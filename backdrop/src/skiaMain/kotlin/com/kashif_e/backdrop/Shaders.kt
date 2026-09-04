/*
   Copyright 2025 Kyant

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package com.kashif_e.backdrop

/**
 * SkSL shader strings for iOS/Skia backend.
 * These are ports of the AGSL shaders used on Android.
 */

private const val RoundedRectSDF = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, float2(0.0))) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, float2(0.0)));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}"""

/**
 * Lens/refraction shader for rounded rectangles.
 * Creates a physically accurate glass bevel distortion effect.
 */
internal const val GlassRefractionShaderString = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

$RoundedRectSDF

float opticalBevel(float x) {
    float t = clamp(x, 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (sd > 0.0) {
        return content.eval(coord);
    }
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    
    float u = 1.0 - (-sd / refractionHeight);
    float d = opticalBevel(u) * refractionAmount;
    float edgeDamp = smoothstep(0.0, 1.5, -sd);
    d *= edgeDamp;
    
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
    
    float2 refractedCoord = coord - d * grad;
    return content.eval(refractedCoord);
}"""

/**
 * Lens/refraction shader with 7-band spectral dispersion (chromatic aberration).
 * Creates prismatic color separation at bevel extremes.
 */
internal const val GlassDispersionShaderString = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

$RoundedRectSDF

float opticalBevel(float x) {
    float t = clamp(x, 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (sd > 0.0) {
        return content.eval(coord);
    }
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    
    float u = 1.0 - (-sd / refractionHeight);
    float d = opticalBevel(u) * refractionAmount;
    float edgeDamp = smoothstep(0.0, 1.5, -sd);
    d *= edgeDamp;
    
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
    
    float2 refractedCoord = coord - d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = -d * grad * dispersionIntensity;
    
    half4 color = half4(0.0);
    
    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;
    
    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;
    
    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;
    
    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;
    
    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;
    
    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;
    
    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;
    
    return color;
}"""

/**
 * Highlight shader for directional lighting effect.
 * Creates a gradient-based highlight based on surface normal and light direction.
 */
internal const val DefaultHighlightShaderString = """
uniform float2 size;
uniform float4 cornerRadii;
uniform float angle;
uniform float falloff;

$RoundedRectSDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 normal = float2(cos(angle), sin(angle));
    float d = dot(grad, normal);
    float intensity = pow(abs(d), falloff);
    return half4(1.0) * intensity;
}"""

/**
 * Ambient highlight shader with one-sided lighting.
 * Creates a highlight that only appears on one side of the surface.
 */
internal const val AmbientHighlightShaderString = """
uniform float2 size;
uniform float4 cornerRadii;
uniform float angle;
uniform float falloff;

$RoundedRectSDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 normal = float2(cos(angle), sin(angle));
    float d = dot(grad, normal);
    float intensity = pow(abs(d), falloff);
    float t = step(0.0, d);
    return half4(t, t, t, 1.0) * intensity;
}"""

/**
 * Gamma adjustment shader for brightness/contrast control.
 */
internal const val GammaAdjustmentShaderString = """
uniform shader content;

uniform float power;

half4 main(float2 coord) {
    half4 color = content.eval(coord);
    color.r = pow(color.r, power);
    color.g = pow(color.g, power);
    color.b = pow(color.b, power);
    return color;
}"""

/**
 * Reflective glass shader with wave distortion, chromatic aberration, and vignette.
 * Creates a dynamic glass surface effect with rippling distortions.
 * 
 * SkSL port of the AGSL shader for iOS/Desktop/Web platforms.
 */
internal const val ReflectiveGlassShaderString = """
uniform shader content;
uniform float2 size;
uniform float reflectionStrength;
uniform float distortionAmount;
uniform float chromaticAberration;
uniform float vignetteStrength;

half4 main(float2 coord) {
    // Normalize coordinates to 0-1 range
    float2 uv = coord / size;
    float2 center = float2(0.5, 0.5);
    float2 dir = uv - center;
    float dist = length(dir);
    
    // Create wave/ripple distortion pattern for glass-like effect
    float wave1 = sin(uv.x * 12.0 + uv.y * 8.0) * 0.5 + 0.5;
    float wave2 = sin(uv.y * 10.0 - uv.x * 6.0) * 0.5 + 0.5;
    float wave3 = sin((uv.x + uv.y) * 14.0) * 0.5 + 0.5;
    
    // Combine waves for organic glass distortion
    float2 waveOffset = float2(
        (wave1 - 0.5) * 2.0 + (wave3 - 0.5),
        (wave2 - 0.5) * 2.0 + (wave3 - 0.5)
    );
    
    // Apply radial falloff so edges distort more (like curved glass)
    float edgeFactor = smoothstep(0.0, 0.7, dist);
    float2 distortedUv = uv + waveOffset * distortionAmount * 0.1 * (0.3 + edgeFactor * 0.7);
    
    // Keep coordinates in valid range
    distortedUv = clamp(distortedUv, float2(0.0), float2(1.0));
    float2 distortedCoord = distortedUv * size;
    
    half4 col;
    
    // Apply chromatic aberration (color separation) for prismatic glass effect
    if (chromaticAberration > 0.001) {
        // Radial chromatic aberration - colors separate from center
        float2 aberrationDir = dir * chromaticAberration * size.x * 0.5;
        
        float2 rCoord = clamp(distortedCoord + aberrationDir * 1.2, float2(0.0), size);
        float2 gCoord = distortedCoord;
        float2 bCoord = clamp(distortedCoord - aberrationDir * 1.2, float2(0.0), size);
        
        half4 rChannel = content.eval(rCoord);
        half4 gChannel = content.eval(gCoord);
        half4 bChannel = content.eval(bCoord);
        
        col = half4(rChannel.r, gChannel.g, bChannel.b, 1.0);
    } else {
        col = content.eval(distortedCoord);
    }
    
    // Add subtle highlight/specular reflection on the glass surface
    float highlight = pow(1.0 - dist, 3.0) * 0.15 * reflectionStrength;
    col.rgb += half3(highlight);
    
    // Add edge glow effect simulating light bending at glass edges
    float edgeGlow = smoothstep(0.3, 0.6, dist) * (1.0 - smoothstep(0.6, 0.8, dist));
    col.rgb += half3(edgeGlow * 0.1 * reflectionStrength);
    
    // Apply vignette effect (darken edges for depth)
    float vignette = 1.0 - smoothstep(0.2, 1.0, dist) * vignetteStrength;
    col.rgb *= vignette;
    
    // Blend with original based on reflection strength
    half4 originalColor = content.eval(coord);
    col = mix(originalColor, col, reflectionStrength);
    
    return col;
}"""

/**
 * Dual-tone physical glass bevel shader for Apple-style 3D glass edge lighting.
 * Generates top specular rim highlight and bottom counter-shadow in a single pass.
 */
internal const val GlassBevelShaderString = """
uniform float2 size;
uniform float4 cornerRadii;
uniform float angle;
uniform float falloff;
uniform float specularAlpha;
uniform float shadowAlpha;

$RoundedRectSDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 lightDir = float2(cos(angle), sin(angle));
    
    float nDotL = dot(grad, lightDir);
    float specular = pow(max(0.0, nDotL), falloff) * specularAlpha;
    float shadow = pow(max(0.0, -nDotL), falloff) * shadowAlpha;
    
    float a = clamp(specular + shadow, 0.0, 1.0);
    return half4(specular, specular, specular, a);
}"""

/**
 * Interactive cursor/pointer tracking specular highlight shader.
 * The specular glint dynamically tracks cursor position along the glass rim.
 */
internal const val GlassInteractiveHighlightShaderString = """
uniform float2 size;
uniform float4 cornerRadii;
uniform float2 pointerPos;
uniform float falloff;
uniform float intensity;

$RoundedRectSDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    
    float2 toLight = pointerPos - coord;
    float dist = length(toLight);
    float2 lightDir = dist > 0.001 ? normalize(toLight) : float2(0.0, 1.0);
    
    float nDotL = max(0.0, dot(grad, lightDir));
    float maxDim = max(size.x, size.y);
    float proximity = 1.0 - smoothstep(0.0, maxDim * 1.2, dist);
    
    float shine = pow(nDotL, falloff) * proximity * intensity;
    return half4(shine, shine, shine, shine);
}"""

/**
 * Procedural blue-noise micro-dither shader to eliminate 8-bit color banding
 * on heavy blurred backdrops and impart a tactile frosted glass finish.
 */
internal const val GlassGrainShaderString = """
uniform shader content;
uniform float2 size;
uniform float intensity;

float hash(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

half4 main(float2 coord) {
    half4 col = content.eval(coord);
    float noise = (hash(coord) - 0.5) * 2.0 * intensity;
    col.rgb += half3(noise);
    return col;
}"""

