package io.github.amjadsmarzug.xmousebutton.model;

import java.util.List;

/** An ordered list of profiles. The first non-fallback profile whose rules match the window wins. */
public final class Config {

    private final List<Profile> profiles;
    private final Profile fallback;

    public Config(List<Profile> profiles) {
        this.profiles = List.copyOf(profiles);
        this.fallback = profiles.stream()
                .filter(Profile::isFallback)
                .findFirst()
                .orElseGet(() -> Profile.empty(Profile.FALLBACK_NAME));
    }

    public List<Profile> profiles() {
        return profiles;
    }

    public Profile resolve(WindowInfo window) {
        for (Profile profile : profiles) {
            if (!profile.isFallback() && profile.matches(window)) {
                return profile;
            }
        }
        return fallback;
    }
}
