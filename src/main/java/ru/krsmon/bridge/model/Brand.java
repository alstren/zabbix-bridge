package ru.krsmon.bridge.model;

import lombok.Getter;
import org.springframework.lang.NonNull;

public enum Brand {
    KEENETIC("(config)>", "show ip arp", "tools ping %s count 1"),
    MICROTIC(null, "ip arp print", "ping address=%s count=1"),
    DLINK("$", "cat /proc/net/arp", "ping %s -c 1"),
    ASUS("#", "cat /proc/net/arp", "ping %s -c 1 -W 3"),
    OPENWRT(null, "cat /proc/net/arp", "ping %s -c 1"),
    TPLINK(null, null, null);

    @Getter
    private final String invite;

    @Getter private final String arp;

    private final String ping;

    Brand(String invite, String arp, String ping) {
        this.invite = invite;
        this.arp = arp;
        this.ping = ping;
    }

    public String getPing(@NonNull String ip) {
        return ping.formatted(ip);
    }
}
