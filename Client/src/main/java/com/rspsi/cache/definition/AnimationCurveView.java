package com.rspsi.cache.definition;

import java.util.Arrays;

/**
 * Integer-frame evaluator for the cached-model animation curve format used by
 * the current OSRS client. The source client pre-samples the same curves after
 * decode; retaining the keys here keeps the neutral cache boundary compact.
 */
public final class AnimationCurveView {
    public enum Extrapolation {
        CONSTANT,
        LINEAR,
        CYCLE,
        CYCLE_OFFSET,
        OSCILLATE;

        public static Extrapolation fromOrdinal(int ordinal) {
            return switch (ordinal) {
                case 1 -> LINEAR;
                case 2 -> CYCLE;
                case 3 -> CYCLE_OFFSET;
                case 4 -> OSCILLATE;
                default -> CONSTANT;
            };
        }
    }

    public record Key(
            int frame,
            float value,
            float incomingTime,
            float incomingValue,
            float outgoingTime,
            float outgoingValue
    ) { }

    private static final float EPSILON = Math.ulp(1.0f);

    private final Extrapolation before;
    private final Extrapolation after;
    private final boolean bezier;
    private final Key[] keys;

    public AnimationCurveView(Extrapolation before, Extrapolation after,
                              boolean bezier, Key[] keys) {
        this.before = before == null ? Extrapolation.CONSTANT : before;
        this.after = after == null ? Extrapolation.CONSTANT : after;
        this.bezier = bezier;
        if (keys == null || keys.length == 0) {
            throw new IllegalArgumentException("Animation curve needs at least one key");
        }
        this.keys = keys.clone();
        for (int index = 1; index < this.keys.length; index++) {
            if (this.keys[index].frame() < this.keys[index - 1].frame()) {
                throw new IllegalArgumentException("Animation curve keys must be ordered");
            }
        }
    }

    public Extrapolation before() { return before; }
    public Extrapolation after() { return after; }
    public boolean bezier() { return bezier; }
    public Key[] keys() { return keys.clone(); }

    public float valueAt(int frame) {
        float time = frame;
        Key first = keys[0];
        Key last = keys[keys.length - 1];
        if (time < first.frame()) {
            return before == Extrapolation.CONSTANT ? first.value() : extrapolate(time, true);
        }
        if (time > last.frame()) {
            return after == Extrapolation.CONSTANT ? last.value() : extrapolate(time, false);
        }
        return interpolate(time);
    }

    private float interpolate(float time) {
        if (keys.length == 1) return keys[0].value();
        int index = segment(time);
        Key current = keys[index];
        Key next = index + 1 < keys.length ? keys[index + 1] : null;
        if (next == null || time == current.frame()) return current.value();

        if (current.outgoingTime() == 0.0f && current.outgoingValue() == 0.0f) {
            return current.value();
        }
        if (current.outgoingTime() == Float.MAX_VALUE
                && current.outgoingValue() == Float.MAX_VALUE) {
            return time != current.frame() ? next.value() : current.value();
        }

        float t0 = current.frame();
        float v0 = current.value();
        float t1 = next.frame();
        float v1 = next.value();
        float c1x = t0 + current.outgoingTime() / 3.0f;
        float c1y = v0 + current.outgoingValue() / 3.0f;
        float c2x = t1 - next.incomingTime() / 3.0f;
        float c2y = v1 - next.incomingValue() / 3.0f;

        if (!bezier) {
            float span = t1 - t0;
            if (span == 0.0f) return v0;
            float slope0 = current.outgoingTime() == 0.0f
                    ? 0.0f : current.outgoingValue() / current.outgoingTime();
            float slope1 = next.incomingTime() == 0.0f
                    ? 0.0f : next.incomingValue() / next.incomingTime();
            float u = (time - t0) / span;
            float u2 = u * u;
            float u3 = u2 * u;
            return (2.0f * u3 - 3.0f * u2 + 1.0f) * v0
                    + (u3 - 2.0f * u2 + u) * span * slope0
                    + (-2.0f * u3 + 3.0f * u2) * v1
                    + (u3 - u2) * span * slope1;
        }

        float span = t1 - t0;
        if (span == 0.0f) return v0;
        float x1 = (c1x - t0) / span;
        float x2 = (c2x - t0) / span;
        float originalX1 = x1;
        float originalX2 = x2;
        if (x1 < 0.0f) x1 = 0.0f;
        if (x2 > 1.0f) x2 = 1.0f;
        if (x1 > 1.0f || x2 < -1.0f) {
            x2 = 1.0f - x2;
            if (x1 < 0.0f) x1 = 0.0f;
            if (x2 < 0.0f) x2 = 0.0f;
            if (x1 > 1.0f || x2 > 1.0f) {
                float discriminant = (x1 - 2.0f + x2) * x1
                        + (x2 - 2.0f) * x2 + 1.0f;
                if (discriminant + EPSILON > 0.0f) {
                    float a = x1 - 2.0f;
                    float b = x1 - 1.0f;
                    float root = (float)Math.sqrt(a * a - 4.0f * b * b);
                    float upper = (-a + root) * 0.5f;
                    if (x2 > upper + EPSILON) {
                        x2 = upper - EPSILON;
                    } else {
                        float lower = (-a - root) * 0.5f;
                        if (x2 < lower + EPSILON) x2 = lower + EPSILON;
                    }
                }
            }
            x2 = 1.0f - x2;
        }

        if (x1 != originalX1 && originalX1 != 0.0f) {
            c1y = v0 + x1 * (c1y - v0) / originalX1;
        }
        if (x2 != originalX2 && originalX2 != 1.0f) {
            c2y = v1 - (1.0f - x2) * (v1 - c2y) / (1.0f - originalX2);
        }

        float normalized = (time - t0) / span;
        float parameter = solveBezierTime(normalized, x1, x2);
        return cubicBezier(v0, c1y, c2y, v1, parameter);
    }

    private int segment(float time) {
        int low = 0;
        int high = keys.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (keys[mid].frame() <= time) {
                if (mid == keys.length - 1 || keys[mid + 1].frame() > time) return mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return 0;
    }

    private float extrapolate(float time, boolean beforeRange) {
        Key first = keys[0];
        Key last = keys[keys.length - 1];
        float start = first.frame();
        float end = last.frame();
        float span = end - start;
        if (span == 0.0f) return first.value();

        Extrapolation mode = beforeRange ? before : after;
        if (mode == Extrapolation.LINEAR) {
            if (beforeRange) {
                float distance = start - time;
                return first.incomingTime() == 0.0f
                        ? first.value()
                        : first.value() - first.incomingValue() * distance / first.incomingTime();
            }
            float distance = time - end;
            return last.outgoingTime() == 0.0f
                    ? last.value()
                    : last.value() + last.outgoingValue() * distance / last.outgoingTime();
        }

        float ratio = beforeRange ? (time - start) / span : (time - end) / span;
        float whole = (float)((int)ratio);
        float fraction = Math.abs(ratio - whole);
        float local = span * fraction;
        float cycleCount = Math.abs(1.0f + whole);
        float parity = cycleCount / 2.0f - (float)((int)(cycleCount / 2.0f));

        if (beforeRange) {
            if (mode == Extrapolation.OSCILLATE) {
                local = parity != 0.0f ? local + start : end - local;
            } else if (mode == Extrapolation.CYCLE || mode == Extrapolation.CYCLE_OFFSET) {
                local = end - local;
            } else {
                return first.value();
            }
        } else {
            if (mode == Extrapolation.OSCILLATE) {
                local = parity != 0.0f ? end - local : local + start;
            } else if (mode == Extrapolation.CYCLE || mode == Extrapolation.CYCLE_OFFSET) {
                local += start;
            } else {
                return last.value();
            }
        }

        float value = interpolate(local);
        if (mode == Extrapolation.CYCLE_OFFSET) {
            float delta = last.value() - first.value();
            value += (beforeRange ? -cycleCount : cycleCount) * delta;
        }
        return value;
    }

    private static float solveBezierTime(float x, float c1, float c2) {
        if (c1 == 1.0f / 3.0f && c2 == 2.0f / 3.0f) return x;
        float low = 0.0f;
        float high = 1.0f;
        for (int iteration = 0; iteration < 28; iteration++) {
            float mid = (low + high) * 0.5f;
            float value = cubicBezier(0.0f, c1, c2, 1.0f, mid);
            if (value < x) low = mid;
            else high = mid;
        }
        return (low + high) * 0.5f;
    }

    private static float cubicBezier(float a, float b, float c, float d, float t) {
        float oneMinus = 1.0f - t;
        return oneMinus * oneMinus * oneMinus * a
                + 3.0f * oneMinus * oneMinus * t * b
                + 3.0f * oneMinus * t * t * c
                + t * t * t * d;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AnimationCurveView value)) return false;
        return before == value.before && after == value.after
                && bezier == value.bezier && Arrays.equals(keys, value.keys);
    }

    @Override
    public int hashCode() {
        int result = 31 * before.hashCode() + after.hashCode();
        result = 31 * result + Boolean.hashCode(bezier);
        return 31 * result + Arrays.hashCode(keys);
    }
}
