package com.archer.jdknet.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.InvalidKeyException;
import java.security.KeyException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;


public class CertAndKeyUtil {
	public static final String ALG_EC = "EC";
	public static final String ALG_RSA = "RSA";
	public static final String ALG_DSA = "DSA";

	private static final String KEY_ALIAS = "key";

	private static final String KEYSTORE_TYPE = "PKCS12";

	private static final String MANAGER_ALGORITHM = "sunx509";
	
	private static final Pattern CERT_PATTERN = Pattern.compile(
            "-+BEGIN\\s+.*CERTIFICATE[^-]*-+(?:\\s|\\r|\\n)+" + // Header
                    "([a-z0-9+/=\\r\\n]+)" +                    // Base64 text
                    "-+END\\s+.*CERTIFICATE[^-]*-+",            // Footer
            Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY_PATTERN = Pattern.compile(
            "-+BEGIN\\s+.*PRIVATE\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+" + // Header
                    "([a-z0-9+/=\\r\\n]+)" +                       // Base64 text
                    "-+END\\s+.*PRIVATE\\s+KEY[^-]*-+",            // Footer
            Pattern.CASE_INSENSITIVE);
   
    

	public static TrustManager[] buildTrustManagers(String crtPath) throws Exception {
		return buildTrustManagers(crtPath, null);
	}
	
	public static TrustManager[] buildTrustManagers(InputStream crt) throws Exception {
    	return buildTrustManagers(crt, null);
	}
	
	public static TrustManager[] buildTrustManagers(String crtPath, String provider) throws Exception {
		try(InputStream crt = new FileInputStream(crtPath)) {
			return buildTrustManagers(crt, provider);
		}
	}
	
	public static TrustManager[] buildTrustManagers(InputStream crt, String provider) throws Exception {
    	X509Certificate[] caCerts = CertAndKeyUtil.getCertificates(crt, provider);
    	TrustManagerFactory tmf = CertAndKeyUtil.buildTrustManagerFactory(caCerts, KEYSTORE_TYPE, MANAGER_ALGORITHM, provider);
    	return tmf.getTrustManagers();
	}
	

	public static KeyManager[] buildKeyManagers(String keyPath, String crtPath, String password) throws Exception {
		try(InputStream keyIn = new FileInputStream(keyPath);
				InputStream crtIn = new FileInputStream(crtPath);) {
			return buildKeyManagers(keyIn, crtIn, password, null);
		}
	}
	
	public static KeyManager[] buildKeyManagers(InputStream keyIn, InputStream crtIn, String password) throws Exception {
		return buildKeyManagers(keyIn, crtIn, password, null);
	}
	
	public static KeyManager[] buildKeyManagers(String keyPath, String crtPath, String password, String provider) throws Exception {
		try(InputStream keyIn = new FileInputStream(keyPath);
				InputStream crtIn = new FileInputStream(crtPath);) {
			return buildKeyManagers(keyIn, crtIn, password, provider);
		}
	}
	
	public static KeyManager[] buildKeyManagers(InputStream keyIn, InputStream crtIn, String password, String provider) throws Exception {
		char[] pwdChars = password == null?null:password.toCharArray();
    	PrivateKey key = CertAndKeyUtil.getPrivateKey(keyIn, password, provider);
    	X509Certificate[] sslCerts = CertAndKeyUtil.getCertificates(crtIn, provider);
    	KeyStore ks = CertAndKeyUtil.buildKeyStore(sslCerts, key, pwdChars, KEYSTORE_TYPE, provider);
    	KeyManagerFactory kmf = CertAndKeyUtil.buildKeyManagerFactory(ks, pwdChars, MANAGER_ALGORITHM);
    	return kmf.getKeyManagers();
	}
    
    public static TrustManagerFactory buildTrustManagerFactory(
            X509Certificate[] certCollection, String keyStoreType, String algorithm)
            throws NoSuchAlgorithmException, CertificateException, KeyStoreException, 
            IOException, NoSuchProviderException {
    	return buildTrustManagerFactory(certCollection, keyStoreType, algorithm, (String)null);
    }
    
    public static TrustManagerFactory buildTrustManagerFactory(
            X509Certificate[] certCollection, String keyStoreType, String algorithm, String provider)
            throws NoSuchAlgorithmException, CertificateException, KeyStoreException, 
            IOException, NoSuchProviderException {
        if (keyStoreType == null) {
            keyStoreType = KeyStore.getDefaultType();
        }
        final KeyStore ks;
        if(provider == null) {
        	ks = KeyStore.getInstance(keyStoreType);
        } else {
        	ks = KeyStore.getInstance(keyStoreType, provider);
        }
        ks.load(null, null);

        int i = 1;
        for (X509Certificate cert: certCollection) {
            String alias = Integer.toString(i);
            ks.setCertificateEntry(alias, cert);
            i++;
        }

        return buildTrustManagerFactory(ks, algorithm);
    }
    
    public static TrustManagerFactory buildTrustManagerFactory(
            X509Certificate[] certCollection, String keyStoreType, String algorithm, Provider provider)
            throws NoSuchAlgorithmException, CertificateException, KeyStoreException, 
            IOException, NoSuchProviderException {
        if (keyStoreType == null) {
            keyStoreType = KeyStore.getDefaultType();
        }
        final KeyStore ks;
        if(provider == null) {
        	ks = KeyStore.getInstance(keyStoreType);
        } else {
        	ks = KeyStore.getInstance(keyStoreType, provider);
        }
        ks.load(null, null);

        int i = 1;
        for (X509Certificate cert: certCollection) {
            String alias = Integer.toString(i);
            ks.setCertificateEntry(alias, cert);
            i++;
        }

        return buildTrustManagerFactory(ks, algorithm);
    }
    
    public static TrustManagerFactory buildTrustManagerFactory(
    		KeyStore ks, String algorithm) 
    				throws KeyStoreException, NoSuchAlgorithmException {
    	
    	if(algorithm == null) {
        	algorithm = TrustManagerFactory.getDefaultAlgorithm();
        }
    	TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(algorithm);

        trustManagerFactory.init(ks);

        return trustManagerFactory;
    }
    
    public static KeyManagerFactory buildKeyManagerFactory(
    		KeyStore keyStore, char[] keyPasswordChars, String algorithm) 
    		throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException {
    	if(algorithm == null) {
        	algorithm = KeyManagerFactory.getDefaultAlgorithm();
        }
    	KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
    	
    	keyManagerFactory.init(keyStore, keyPasswordChars);
    	
    	return keyManagerFactory;
    }
    
    public static KeyStore buildKeyStore(X509Certificate[] certChain, PrivateKey key,
            char[] keyPasswordChars, String keyStoreType, String keyAlias)
            		throws IOException, KeyStoreException, NoSuchAlgorithmException, 
            		CertificateException, NoSuchProviderException { 
    	return buildKeyStore(certChain, key, keyPasswordChars, keyStoreType, (String)null, keyAlias);
    }
    
    public static KeyStore buildKeyStore(X509Certificate[] certChain, PrivateKey key,
            char[] keyPasswordChars, String keyStoreType, String provider, String keyAlias)
            		throws IOException, KeyStoreException, NoSuchAlgorithmException, 
            		CertificateException, NoSuchProviderException {
    	if(keyAlias == null) {
    		keyAlias = KEY_ALIAS;
    	}
		if (keyStoreType == null) {
			keyStoreType = KeyStore.getDefaultType();
		}
		KeyStore ks;
        if(provider == null) {
        	ks = KeyStore.getInstance(keyStoreType);
        } else {
        	ks = KeyStore.getInstance(keyStoreType, provider);
        }
		ks.load(null, null);
		ks.setKeyEntry(keyAlias, key, keyPasswordChars, certChain);
		return ks;
	}
    
    public static KeyStore buildKeyStore(X509Certificate[] certChain, PrivateKey key,
            char[] keyPasswordChars, String keyStoreType, Provider provider, String keyAlias)
            		throws IOException, KeyStoreException, NoSuchAlgorithmException, 
            		CertificateException, NoSuchProviderException {
    	if(keyAlias == null) {
    		keyAlias = KEY_ALIAS;
    	}
		if (keyStoreType == null) {
			keyStoreType = KeyStore.getDefaultType();
		}
		KeyStore ks;
        if(provider == null) {
        	ks = KeyStore.getInstance(keyStoreType);
        } else {
        	ks = KeyStore.getInstance(keyStoreType, provider);
        }
		ks.load(null, null);
		ks.setKeyEntry(keyAlias, key, keyPasswordChars, certChain);
		return ks;
	}
    
    public static X509Certificate[] getCertificates(File cert) 
    		throws CertificateException {
    	return getCertificates(cert, (String)null);
	}
    
    public static X509Certificate[] getCertificates(InputStream certIn) 
    		throws CertificateException {
    	return getCertificates(certIn, (String)null);
	}
    
    public static X509Certificate[] getCertificates(InputStream certIn, String provider) 
    		throws CertificateException {
    	return getCertificatesFromBuffers(readCertificates(certIn), provider);
	}
    
    public static X509Certificate[] getCertificates(File cert, String provider) 
    		throws CertificateException {
    	return getCertificatesFromBuffers(readCertificates(cert));
	}
    
	public static X509Certificate[] getCertificatesFromBuffers(byte[][] certs) 
			throws CertificateException {
	    return getCertificatesFromBuffers(certs, (String)null);
	}
	
	public static X509Certificate[] getCertificatesFromBuffers(byte[][] certs, String provider) 
			throws CertificateException {
	    CertificateFactory cf;
		try {
			if(provider != null) {
				cf = CertificateFactory.getInstance("X.509", provider);
			} else {
				cf = CertificateFactory.getInstance("X.509");
			}
		} catch (NoSuchProviderException e) {
			throw new CertificateException(e);
		}
	    return getCertificatesFromBuffers0(certs, cf);
	}
	
	public static X509Certificate[] getCertificatesFromBuffers(byte[][] certs, Provider provider) 
			throws CertificateException {

	    CertificateFactory cf;
		if(provider != null) {
			cf = CertificateFactory.getInstance("X.509", provider);
		} else {
			cf = CertificateFactory.getInstance("X.509");
		}
	    return getCertificatesFromBuffers0(certs, cf);
	}
	
	static X509Certificate[] getCertificatesFromBuffers0(byte[][] certs, CertificateFactory cf) 
			throws CertificateException {
		X509Certificate[] x509Certs = new X509Certificate[certs.length];

        for (int i = 0; i < certs.length; i++) {
            try(InputStream is = new ByteArrayInputStream(certs[i])) {
                x509Certs[i] = (X509Certificate) cf.generateCertificate(is);
            } catch (IOException e) {
            	throw new CertificateException(e);
			}
        }
	    return x509Certs;
	} 
	
	
	public static byte[][] readCertificates(File file) throws CertificateException {
        try {
            InputStream in = new FileInputStream(file);

            try {
                return readCertificates(in);
            } finally {
                try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
            }
        } catch (FileNotFoundException e) {
            throw new CertificateException("could not find certificate file: " + file);
        }
    }

    public static byte[][] readCertificates(InputStream in) throws CertificateException {
        String content;
        try {
            content = readContent(in);
        } catch (IOException e) {
            throw new CertificateException("failed to read certificate input stream", e);
        }

        Matcher m = CERT_PATTERN.matcher(content);
        List<byte[]> certs = new ArrayList<>();
        int start = 0;
        for (;;) {
            if (!m.find(start)) {
                break;
            }
            byte[] keyBytes = null;
            try {
                keyBytes = m.group(1).getBytes("US-ASCII");
    		} catch (UnsupportedEncodingException e) {
    			throw new CertificateException(e);
    		}
            try {
            	certs.add(Base64.getMimeDecoder().decode(keyBytes));
            } catch(Exception mimeDecode) {
            	try {
            		certs.add(Base64.getDecoder().decode(keyBytes));
            	}catch(Exception decode) {
            		throw new CertificateException("invalid character in base64 decoder.", decode);
            	}
            }
            start = m.end();
        }

        if (certs.isEmpty()) {
            throw new CertificateException("found no certificates in input stream");
        }
        
        return certs.toArray(new byte[0][]);
    }

    public static byte[] readPrivateKey(File file) throws KeyException {
        try {
            InputStream in = new FileInputStream(file);

            try {
                return readPrivateKey(in);
            } finally {
                try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
            }
        } catch (FileNotFoundException e) {
            throw new KeyException("could not find key file: " + file);
        }
    }

    public static byte[] readPrivateKey(InputStream in) throws KeyException {
        String content;
        try {
            content = readContent(in);
        } catch (IOException e) {
            throw new KeyException("failed to read key input stream", e);
        }

        Matcher m = KEY_PATTERN.matcher(content);
        if (!m.find()) {
        	
            throw new KeyException("could not find a PKCS #8 private key in input stream.");
        }
        byte[] keyBytes = null;
        try {
            keyBytes = m.group(1).getBytes("US-ASCII");
		} catch (UnsupportedEncodingException e) {
			throw new KeyException(e);
		}
        try {
    		return Base64.getMimeDecoder().decode(keyBytes);
        } catch(Exception mimeDecode) {
        	try {
        		return Base64.getDecoder().decode(keyBytes);
        	}catch(Exception decode) {
        		throw new KeyException("invalid character in base64 decoder.", decode);
        	}
        }
    }
    
    static String readContent(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[8192];
            for (;;) {
                int ret = in.read(buf);
                if (ret < 0) {
                    break;
                }
                out.write(buf, 0, ret);
            }
            return out.toString("US-ASCII");
        } finally {
            out.close();
        }
    }

    public static PrivateKey getPrivateKey(File keyFile, String keyPassword) 
    		throws InvalidKeySpecException, InvalidKeyException, KeyException {
        if (keyFile == null) {
            return null;
        }
        return getPrivateKey(readPrivateKey(keyFile), keyPassword);
    }
    
    public static PrivateKey getPrivateKey(File keyFile, String keyPassword, String keyFactoryProvider) 
    		throws InvalidKeySpecException, InvalidKeyException, KeyException {
        if (keyFile == null) {
            return null;
        }
        return getPrivateKey(readPrivateKey(keyFile), keyPassword, keyFactoryProvider);
    }
    
    public static PrivateKey getPrivateKey(File keyFile, String keyPassword, 
    		String keyFactoryProvider, AlgorithmParameters param) 
    		throws InvalidKeySpecException, InvalidKeyException, KeyException {
        if (keyFile == null) {
            return null;
        }
        return getPrivateKey(readPrivateKey(keyFile), keyPassword, param, keyFactoryProvider);
    }

    public static PrivateKey getPrivateKey(InputStream keyInputStream, String keyPassword)
            throws InvalidKeySpecException, InvalidKeyException, KeyException {
        if (keyInputStream == null) {
            return null;
        }
        return getPrivateKey(readPrivateKey(keyInputStream), keyPassword);
    }

    public static PrivateKey getPrivateKey(InputStream keyInputStream, String keyPassword, String provider)
            throws InvalidKeySpecException, InvalidKeyException, KeyException {
        if (keyInputStream == null) {
            return null;
        }
        return getPrivateKey(readPrivateKey(keyInputStream), keyPassword, provider);
    }
    

    public static PrivateKey getPrivateKey(InputStream keyInputStream, String keyPassword, 
    		String provider, AlgorithmParameters param)
            throws InvalidKeySpecException, InvalidKeyException, KeyException {
        if (keyInputStream == null) {
            return null;
        }
        return getPrivateKey(readPrivateKey(keyInputStream), keyPassword, param, provider);
    }
    

    public static PrivateKey getPrivateKey(byte[] encodedKey, String keyPassword)
            throws InvalidKeyException {
    	return getPrivateKey(encodedKey, keyPassword, null);
    }
    
    public static PrivateKey getPrivateKey(byte[] encodedKey, String keyPassword, String provider)
            throws InvalidKeyException {
    	return getPrivateKey(encodedKey, keyPassword, null, provider);
    }
    
    public static PrivateKey getPrivateKey(byte[] encodedKey, String keyPassword, AlgorithmParameters param, String provider)
            throws InvalidKeyException {
    	char[] pwd = keyPassword == null?null:keyPassword.toCharArray();
    	String[] algorithms = {"RSA", "DSA", "EC"};
    	for(String al: algorithms) {
    		try {
    			KeyFactory kf;
    			if(provider == null) {
    				kf = KeyFactory.getInstance(al);
    			} else {
    				kf = KeyFactory.getInstance(al,provider);
    			}
        		PKCS8EncodedKeySpec spec = generateKeySpec(pwd, encodedKey, param);
                return getPrivateKey(spec, kf);
            } catch (Exception e) {
            	continue;
            }
    	}
		throw new InvalidKeyException("Neither RSA, DSA nor EC worked");
    }
    
    public static PrivateKey getPrivateKey(PKCS8EncodedKeySpec encodedKeySpec, KeyFactory kf)
            throws InvalidKeyException, InvalidKeySpecException {
    	return kf.generatePrivate(encodedKeySpec);
    }
    
    public static PKCS8EncodedKeySpec generateKeySpec(char[] password, byte[] key) throws InvalidKeySpecException {
    	return generateKeySpec(password, key, null);
    }
    
    public static PKCS8EncodedKeySpec generateKeySpec(char[] password, byte[] key, AlgorithmParameters param)
            throws InvalidKeySpecException {
    	try {
            if (password == null) {
                return new PKCS8EncodedKeySpec(key);
            }
            
            EncryptedPrivateKeyInfo encryptedPrivateKeyInfo;
            if(param == null) {
            	encryptedPrivateKeyInfo = new EncryptedPrivateKeyInfo(key);
            } else {
            	encryptedPrivateKeyInfo = new EncryptedPrivateKeyInfo(param,key);
            }
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(encryptedPrivateKeyInfo.getAlgName());
            PBEKeySpec pbeKeySpec = new PBEKeySpec(password);
            SecretKey pbeKey = keyFactory.generateSecret(pbeKeySpec);

            Cipher cipher = Cipher.getInstance(encryptedPrivateKeyInfo.getAlgName());
            cipher.init(Cipher.DECRYPT_MODE, pbeKey, encryptedPrivateKeyInfo.getAlgParameters());

            return encryptedPrivateKeyInfo.getKeySpec(cipher);
    	} catch(Exception e) {
    		throw new InvalidKeySpecException(e);
    	}
    }
    
    public static String convertPrivateKeyToPemKeyText(PrivateKey key) {
        byte[] buffer = Base64.getEncoder().encode(key.getEncoded());
        String head = "-----BEGIN PRIVATE KEY-----\n";
        String tail = "-----END PRIVATE KEY-----\n";
        StringBuilder sb = new StringBuilder(head);
        int begin = 0, offset = 64;
        byte[] line = new byte[offset];
        while(begin + offset <= buffer.length) {
            System.arraycopy(buffer, begin, line, 0, offset);
            sb.append(new String(line, StandardCharsets.US_ASCII))
                    .append("\n");
            begin += offset;
        }
        if(begin < buffer.length) {
            line = new byte[buffer.length- begin];
            System.arraycopy(buffer, begin, line, 0, line.length);
            sb.append(new String(line, StandardCharsets.UTF_8))
                    .append("\n");
        }
        sb.append(tail);
        return sb.toString();
    }
//
//    public static String convertHexedStringToPemKeyText(
//            String hexedPrivateKey, String algrithm) throws Exception {
//        BigInteger privateKeyValue = new BigInteger(hexedPrivateKey, 16);
//        PrivateKey key = convertBigIntegerToPrivateKey(privateKeyValue, algrithm);
//        return convertPrivateKeyToPemKeyText(key);
//    }
//
//    public static PrivateKey convertHexedStringToPrivateKey(
//            String hexedPrivateKey, String algrithm) throws Exception {
//        BigInteger privateKeyValue = new BigInteger(hexedPrivateKey, 16);
//        return convertBigIntegerToPrivateKey(privateKeyValue, algrithm);
//    }
//
//    public static PrivateKey convertBigIntegerToPrivateKey(BigInteger privateKey, String algrithm)
//            throws Exception {
//    	String[] algorithms = {"RSA", "DSA", "EC"};
//    	for(String al: algorithms) {
//    		if(al.equals(algrithm)) {
//        		try {
//        			KeyFactory kf;
//        			if(provider == null) {
//        				kf = KeyFactory.getInstance(al);
//        			} else {
//        				kf = KeyFactory.getInstance(al,provider);
//        			}
//            		PKCS8EncodedKeySpec spec = generateKeySpec(pwd, encodedKey, param);
//                    return getPrivateKey(spec, kf);
//                } catch (Exception e) {
//                	continue;
//                }
//    		}
//    	}
//		throw new InvalidKeyException("Neither RSA, DSA nor EC worked");
//        org.bouncycastle.jce.spec.ECParameterSpec ecParameterSpec =
//                ECNamedCurveTable.getParameterSpec(algrithm);
//        ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(privateKey, ecParameterSpec);
//        KeyFactory keyFactory =
//                KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
//        return keyFactory.generatePrivate(privateKeySpec);
//    }
}