package org.kde.kdeconnect.Plugins.PingPlugin;

import java.io.IOException;

public class InvalidNtpServerResponseException
        extends IOException {
    InvalidNtpServerResponseException(String detailMessage) {
        super(detailMessage);
    }
}
