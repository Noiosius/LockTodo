package com.noiosius.igdm;
import org.junit.Test;
import static org.junit.Assert.*;

public class NavigationPolicyTest {
    @Test public void permitsOnlySelectedSurface() {
        assertTrue(NavigationPolicy.allowed("https://www.instagram.com/direct/t/123/", "DM", "me"));
        assertFalse(NavigationPolicy.allowed("https://www.instagram.com/", "DM", "me"));
        assertTrue(NavigationPolicy.allowed("https://www.instagram.com/", "STORY", "me"));
        assertTrue(NavigationPolicy.allowed("https://www.instagram.com/stories/friend/123/", "STORY", "me"));
        assertTrue(NavigationPolicy.allowed("https://www.instagram.com/me/", "PROFILE", "me"));
        assertFalse(NavigationPolicy.allowed("https://www.instagram.com/friend/", "PROFILE", "me"));
        assertFalse(NavigationPolicy.allowed("https://www.instagram.com/me/reels/", "PROFILE", "me"));
    }
    @Test public void blocksFeedReelsExploreAndOriginTricks() {
        for (String mode : new String[]{"DM", "STORY", "PROFILE"}) {
            for (String url : new String[]{"https://www.instagram.com/explore/", "https://www.instagram.com/reels/",
                    "https://www.instagram.com/p/abc/", "http://www.instagram.com/direct/inbox/",
                    "https://instagram.com.attacker.test/direct/inbox/", "https://evil@instagram.com/direct/inbox/",
                    "https://www.instagram.com:444/direct/inbox/", "file:///direct/inbox/",
                    "https://www.instagram.com/direct/../explore/", "https://www.instagram.com/direct/%2e%2e/explore/"})
                assertFalse(url, NavigationPolicy.allowed(url, mode, "me"));
        }
    }
    @Test public void keepsAuthenticationButNotAccountActivity() {
        assertTrue(NavigationPolicy.allowed("https://www.instagram.com/accounts/login/?next=%2F", "DM", ""));
        assertTrue(NavigationPolicy.allowed("https://www.instagram.com/challenge/123/", "STORY", ""));
        assertFalse(NavigationPolicy.allowed("https://www.instagram.com/accounts/activity/", "DM", ""));
    }
}
