package com.noiosius.igdm;

import java.net.URI;

/** Top-level navigation only; CDN resources are not filtered here. */
final class NavigationPolicy {
    static boolean auth(String path) {
        return path.matches("^/(accounts/(login|logout|onetap|signup|password|two_factor_authentication|confirm_email)|challenge|checkpoint|two_factor|oauth|consent)(/.*)?$");
    }
    static boolean allowed(String url, String mode, String username) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !("www.instagram.com".equalsIgnoreCase(host) || "instagram.com".equalsIgnoreCase(host))) return false;
            String path = uri.getRawPath();
            if (path == null || path.contains("%") || path.contains("\\") || !uri.normalize().getRawPath().equals(path)) return false;
            if (auth(path)) return true;
            if ("DM".equals(mode)) return path.startsWith("/direct/");
            if ("STORY".equals(mode)) return path.equals("/") || path.startsWith("/stories/");
            return "PROFILE".equals(mode) && validUsername(username)
                    && (path.equalsIgnoreCase("/" + username + "/") || path.equalsIgnoreCase("/" + username));
        } catch (Exception ignored) { return false; }
    }
    static boolean validUsername(String value) {
        return value != null && value.matches("[A-Za-z0-9._]{1,30}") && !value.equals(".") && !value.equals("..");
    }
}
