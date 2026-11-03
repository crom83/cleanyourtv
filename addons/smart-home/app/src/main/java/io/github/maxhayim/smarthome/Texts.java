package io.github.maxhayim.smarthome;

import android.content.Context;

/** {@link Ha.Lang} from the string resources, so states, sections and errors follow the TV's language. */
final class Texts implements Ha.Lang {
    private final Context c;

    Texts(Context c) {
        this.c = c;
    }

    @Override public String unavailable() { return c.getString(R.string.state_unavailable); }
    @Override public String run() { return c.getString(R.string.state_run); }
    @Override public String on() { return c.getString(R.string.state_on); }
    @Override public String off() { return c.getString(R.string.state_off); }
    @Override public String onAt(int brightness) { return c.getString(R.string.state_on_brightness, brightness); }
    @Override public String open() { return c.getString(R.string.state_open); }
    @Override public String closed() { return c.getString(R.string.state_closed); }
    @Override public String locked() { return c.getString(R.string.state_locked); }
    @Override public String unlocked() { return c.getString(R.string.state_unlocked); }

    @Override
    public String section(Ha.Kind k) {
        switch (k) {
            case SCENE: return c.getString(R.string.section_scenes);
            case TOGGLE: return c.getString(R.string.section_switches);
            case CLIMATE: return c.getString(R.string.section_climate);
            case SENSOR: return c.getString(R.string.section_sensors);
            case OPENING: return c.getString(R.string.section_openings);
            default: return c.getString(R.string.section_locks);
        }
    }

    @Override public String refusedToken() { return c.getString(R.string.problem_token); }
    @Override public String notTheApi() { return c.getString(R.string.problem_not_api); }
    @Override public String unreachable() { return c.getString(R.string.problem_unreachable); }
    @Override public String httpError(int code) { return c.getString(R.string.problem_http, code); }
}
