#version 330

// Signed distance to a rounded box centred on the origin (negative inside).
float nebula_sdRoundBox(vec2 p, vec2 halfSize, float radius) {
    radius = min(radius, min(halfSize.x, halfSize.y));
    vec2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

// Abramowitz-Stegun erf approximation (max error ~5e-4), used for analytic gaussian shadows.
float nebula_erf(float x) {
    float s = sign(x);
    float a = abs(x);
    float t = 1.0 / (1.0 + 0.47047 * a);
    float y = 1.0 - (0.3480242 * t - 0.0958798 * t * t + 0.7478556 * t * t * t) * exp(-a * a);
    return s * y;
}

// Coverage of a shape blurred by a gaussian of the given sigma, sampled at signed distance d.
float nebula_gaussianCoverage(float d, float sigma) {
    return 0.5 - 0.5 * nebula_erf(d / (sigma * 1.41421356));
}
