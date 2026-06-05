package com.taskforge.authservice.security;

import com.taskforge.authservice.config.JwtProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class KeyPairProvider {

	private final KeyPair keyPair;

	public KeyPairProvider(JwtProperties properties) {
		this.keyPair = loadOrGenerate(properties);
	}

	public PrivateKey privateKey() {
		return keyPair.getPrivate();
	}

	public RSAPublicKey publicKey() {
		return (RSAPublicKey) keyPair.getPublic();
	}

	private KeyPair loadOrGenerate(JwtProperties properties) {
		if (StringUtils.hasText(properties.privateKeyPath()) && StringUtils.hasText(properties.publicKeyPath())) {
			try {
				return new KeyPair(readPublicKey(properties.publicKeyPath()), readPrivateKey(properties.privateKeyPath()));
			} catch (IOException | GeneralSecurityException ex) {
				throw new IllegalStateException("Unable to load configured RSA key pair", ex);
			}
		}
		return generateDevelopmentKeyPair();
	}

	private PrivateKey readPrivateKey(String path) throws IOException, GeneralSecurityException {
		String pem = Files.readString(Path.of(path), StandardCharsets.UTF_8);
		String normalized = stripPemHeaders(pem, "PRIVATE KEY");
		byte[] decoded = Base64.getDecoder().decode(normalized);
		return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
	}

	private PublicKey readPublicKey(String path) throws IOException, GeneralSecurityException {
		String pem = Files.readString(Path.of(path), StandardCharsets.UTF_8);
		String normalized = stripPemHeaders(pem, "PUBLIC KEY");
		byte[] decoded = Base64.getDecoder().decode(normalized);
		return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
	}

	private String stripPemHeaders(String pem, String label) {
		return pem
				.replace("-----BEGIN " + label + "-----", "")
				.replace("-----END " + label + "-----", "")
				.replaceAll("\\s", "");
	}

	private KeyPair generateDevelopmentKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Unable to generate development RSA key pair", ex);
		}
	}
}
