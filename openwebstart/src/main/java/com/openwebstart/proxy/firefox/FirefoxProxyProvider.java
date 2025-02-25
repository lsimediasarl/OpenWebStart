package com.openwebstart.proxy.firefox;

import com.openwebstart.jvm.os.OperationSystem;
import com.openwebstart.proxy.ProxyProvider;
import com.openwebstart.proxy.config.ConfigBasedProvider;
import com.openwebstart.proxy.config.ProxyConfigurationImpl;
import com.openwebstart.proxy.direct.DirectProxyProvider;
import com.openwebstart.proxy.linux.LinuxProxyProvider;
import com.openwebstart.proxy.mac.MacProxyProvider;
import com.openwebstart.proxy.pac.PacBasedProxyProvider;
import com.openwebstart.proxy.windows.WindowsProxyProvider;
import net.adoptopenjdk.icedteaweb.logging.Logger;
import net.adoptopenjdk.icedteaweb.logging.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import static com.openwebstart.proxy.firefox.FirefoxConstants.AUTO_CONFIG_URL_PROPERTY_NAME;
import static com.openwebstart.proxy.firefox.FirefoxConstants.EXCLUSIONS_PROPERTY_NAME;
import static com.openwebstart.proxy.firefox.FirefoxConstants.FTP_PORT_PROPERTY_NAME;
import static com.openwebstart.proxy.firefox.FirefoxConstants.FTP_PROPERTY_NAME;
import static com.openwebstart.proxy.firefox.FirefoxConstants.HIJACK_LOCALHOST_PROPERTY_NAME;
import static com.openwebstart.proxy.firefox.FirefoxConstants.HTTP_PORT_PROPERTY_NAME;
import static com.openwebstart.proxy.firefox.FirefoxConstants.HTTP_PROPERTY_NAME;
import static com.openwebstart.proxy.firefox.FirefoxConstants.PROXY_TYPE_PROPERTY_NAME;
import static com.openwebstart.proxy.firefox.FirefoxConstants.SHARE_SETTINGS_PROPERTY_NAME;
import static com.openwebstart.proxy.firefox.FirefoxConstants.SOCKS_PORT_PROPERTY_NAME;
import static com.openwebstart.proxy.firefox.FirefoxConstants.SOCKS_PROPERTY_NAME;
import static com.openwebstart.proxy.firefox.FirefoxConstants.SSL_PORT_PROPERTY_NAME;
import static com.openwebstart.proxy.firefox.FirefoxConstants.SSL_PROPERTY_NAME;
import static com.openwebstart.proxy.firefox.FirefoxProxyType.BROWSER_PROXY_TYPE_MANUAL;
import static com.openwebstart.proxy.firefox.FirefoxProxyType.BROWSER_PROXY_TYPE_NONE;
import static com.openwebstart.proxy.firefox.FirefoxProxyType.BROWSER_PROXY_TYPE_PAC;
import static com.openwebstart.proxy.firefox.FirefoxProxyType.BROWSER_PROXY_TYPE_SYSTEM;
import static com.openwebstart.proxy.firefox.FirefoxProxyType.getForConfigValue;
import static com.openwebstart.proxy.util.ProxyConstants.DEFAULT_PROTOCOL_PORT;

public class FirefoxProxyProvider implements ProxyProvider {

    private static final Logger LOG = LoggerFactory.getLogger(FirefoxProxyProvider.class);

    private final ProxyProvider internalProvider;

    public FirefoxProxyProvider() throws Exception {
        final FirefoxPreferences prefs = new FirefoxPreferences();
        prefs.load();

        final int type = prefs.getIntValue(PROXY_TYPE_PROPERTY_NAME, BROWSER_PROXY_TYPE_SYSTEM.getConfigValue());
        final FirefoxProxyType proxyType = getForConfigValue(type);
        LOG.debug("FireFoxProxyType : {}", proxyType);
        if (proxyType == BROWSER_PROXY_TYPE_PAC) {
            internalProvider = createForPac(prefs);
        } else if (proxyType == BROWSER_PROXY_TYPE_MANUAL) {
            internalProvider = createForManualConfig(prefs);
        } else if (proxyType == BROWSER_PROXY_TYPE_NONE) {
            internalProvider = DirectProxyProvider.getInstance();
        } else if (proxyType == BROWSER_PROXY_TYPE_SYSTEM) {
            final OperationSystem localSystem = OperationSystem.getLocalSystem();
            if (localSystem.isWindows()) {
                internalProvider = new WindowsProxyProvider();
            } else if (localSystem.isMac()) {
                internalProvider = new MacProxyProvider();
            } else if (localSystem.isLinux()) {
                internalProvider = new LinuxProxyProvider();
            } else {
                throw new IllegalStateException("Firefox Proxy Type '" + proxyType + "' is not supported for " + localSystem);
            }
        } else {
            throw new IllegalStateException("Firefox Proxy Type '" + proxyType + "' is not supported");
        }
    }

    private ProxyProvider createForManualConfig(final FirefoxPreferences prefs) {
        final ProxyConfigurationImpl proxyConfiguration = new ProxyConfigurationImpl();
        proxyConfiguration.setUseHttpForHttpsAndFtp(prefs.getBooleanValue(SHARE_SETTINGS_PROPERTY_NAME, false));
        proxyConfiguration.setUseHttpForSocks(true);
        proxyConfiguration.setHttpHost(prefs.getStringValue(HTTP_PROPERTY_NAME));
        proxyConfiguration.setHttpPort(prefs.getIntValue(HTTP_PORT_PROPERTY_NAME, DEFAULT_PROTOCOL_PORT));
        proxyConfiguration.setHttpsHost(prefs.getStringValue(SSL_PROPERTY_NAME));
        proxyConfiguration.setHttpsPort(prefs.getIntValue(SSL_PORT_PROPERTY_NAME, DEFAULT_PROTOCOL_PORT));
        proxyConfiguration.setFtpHost(prefs.getStringValue(FTP_PROPERTY_NAME));
        proxyConfiguration.setFtpPort(prefs.getIntValue(FTP_PORT_PROPERTY_NAME, DEFAULT_PROTOCOL_PORT));
        proxyConfiguration.setSocksHost(prefs.getStringValue(SOCKS_PROPERTY_NAME));
        proxyConfiguration.setSocksPort(prefs.getIntValue(SOCKS_PORT_PROPERTY_NAME, DEFAULT_PROTOCOL_PORT));
        proxyConfiguration.setBypassLocal(!prefs.getBooleanValue(HIJACK_LOCALHOST_PROPERTY_NAME, false));

        Arrays.stream(prefs.getStringValue(EXCLUSIONS_PROPERTY_NAME).split("[, ]+"))
                .forEach(proxyConfiguration::addToBypassList);

        return new ConfigBasedProvider(proxyConfiguration);
    }

    private ProxyProvider createForPac(final FirefoxPreferences prefs) throws IOException {
        final String url = prefs.getStringValue(AUTO_CONFIG_URL_PROPERTY_NAME);
        return new PacBasedProxyProvider(new URL(url));
    }

    @Override
    public List<Proxy> select(final URI uri) throws Exception {
        return internalProvider.select(uri);
    }
}
