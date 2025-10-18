package com.phicomm.r1.xiaozhi.util;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;

/**
 * SSL Certificate Trust Manager
 * 
 * ⚠️ WARNING: This class bypasses SSL certificate validation!
 * ⚠️ ONLY USE FOR DEVELOPMENT/TESTING WITH EXPIRED CERTIFICATES!
 * ⚠️ NEVER USE IN PRODUCTION - SECURITY RISK!
 * 
 * Used to connect to servers with expired or self-signed certificates.
 * Enable/disable via XiaozhiConfig.BYPASS_SSL_VALIDATION flag.
 */
public class TrustAllCertificates {
    
    /**
     * Get SSLSocketFactory that trusts all certificates
     * ⚠️ INSECURE - bypasses certificate validation
     */
    public static SSLSocketFactory getSSLSocketFactory() {
        try {
            // Create trust manager that accepts all certificates
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        // Accept all client certificates
                    }
                    
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        // Accept all server certificates (including expired ones)
                    }
                    
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };
            
            // Install the all-trusting trust manager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            
            return sslContext.getSocketFactory();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL socket factory", e);
        }
    }
    
    /**
     * Get HostnameVerifier that accepts all hostnames
     * ⚠️ INSECURE - bypasses hostname verification
     */
    public static HostnameVerifier getAllowAllHostnameVerifier() {
        return new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                // Accept all hostnames (no verification)
                return true;
            }
        };
    }
}