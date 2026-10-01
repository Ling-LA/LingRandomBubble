import com.android.tools.build.apkzlib.zip.*;
import com.android.tools.build.apkzlib.sign.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.zip.*;

/** Offline update of one embedded module. Refuses a different signing certificate.
 * Uses only the manager's published bundled default keystore, never private app data.
 */
public final class UpdateEmbeddedModule {
    private static final String MODULE="assets/lspatch/modules/io.github.ling.randombubble.apk";
    private static String sha(byte[] bytes) throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder out=new StringBuilder(); for(byte b:hash) out.append(String.format("%02x",b)); return out.toString();
    }
    private static String digest(InputStream in) throws Exception {
        try(InputStream stream=in) {
            MessageDigest hash=MessageDigest.getInstance("SHA-256"); byte[] buffer=new byte[65536]; int n;
            while((n=stream.read(buffer))!=-1) hash.update(buffer,0,n);
            StringBuilder out=new StringBuilder(); for(byte b:hash.digest()) out.append(String.format("%02x",b)); return out.toString();
        }
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=5 && args.length!=6) throw new IllegalArgumentException("before candidate module manager expected-certificate-sha256 [realign]");
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        KeyStore.PrivateKeyEntry key=null;
        try(ZipFile manager=new ZipFile(args[3])) {
            for(String asset:new String[]{"assets/keystore","assets/new_keystore"}) {
                ZipEntry entry=manager.getEntry(asset); if(entry==null) continue;
                for(String format:new String[]{"JKS","PKCS12","BKS"}) {
                    try(InputStream input=manager.getInputStream(entry)) {
                        KeyStore store=KeyStore.getInstance(format); store.load(input,"123456".toCharArray());
                        KeyStore.PrivateKeyEntry candidate=(KeyStore.PrivateKeyEntry)store.getEntry("key0",new KeyStore.PasswordProtection("123456".toCharArray()));
                        if(candidate!=null && sha(candidate.getCertificate().getEncoded()).equals(args[4])) { key=candidate; break; }
                    } catch(Exception ignored) { /* a different bundled store format */ }
                }
                if(key!=null) break;
            }
        }
        if(key==null) throw new SecurityException("Bundled default signing certificate does not match installed QQ; no update made");
        Path source=Paths.get(args[0]).toAbsolutePath(), output=Paths.get(args[1]).toAbsolutePath();
        if(source.equals(output) || Files.exists(output)) throw new IllegalArgumentException("Candidate must be a new file");
        ZFileOptions options=new ZFileOptions().setAlignmentRule(AlignmentRules.compose(
                AlignmentRules.constantForSuffix(".so",16384),
                AlignmentRules.constantForSuffix("assets/lspatch/origin.apk",16384), AlignmentRules.constant(4)));
        // Materialize entries into a standard ZIP: overlapping links from LSPatch's
        // compact APK cannot be edited by the stock apkzlib. Keep origin.apk stored
        // and page aligned for the existing loader's original-APK mapping.
        if(args.length==6) Files.copy(source,output);
        try(ZipFile before=new ZipFile(source.toFile()); ZFile apk=ZFile.openReadWrite(output.toFile(),options)) {
            if(before.getEntry(MODULE)==null) throw new IllegalArgumentException("Expected embedded module absent");
            new SigningExtension(SigningOptions.builder().setMinSdkVersion(28).setV2SigningEnabled(true)
                    .setCertificates((X509Certificate[])key.getCertificateChain()).setKey(key.getPrivateKey()).build()).register(apk);
            if(args.length==6 && args[5].equals("update"))
                try(InputStream module=Files.newInputStream(Paths.get(args[2]))) { apk.add(MODULE,module); }
            Enumeration<? extends ZipEntry> entries=before.entries();
            if(args.length==6) entries=Collections.enumeration(Collections.<ZipEntry>emptyList());
            while(entries.hasMoreElements()) {
                ZipEntry entry=entries.nextElement(); String name=entry.getName();
                if(name.startsWith("META-INF/")) continue;
                if(name.equals(MODULE)) {
                    try(InputStream module=Files.newInputStream(Paths.get(args[2]))) { apk.add(name,module); }
                } else {
                    try(InputStream input=before.getInputStream(entry)) {
                        apk.add(name,input,entry.getMethod()!=ZipEntry.STORED && !name.equals("assets/lspatch/origin.apk") && !name.endsWith(".so"));
                    }
                }
            }
            apk.realign();
        }
        int preserved=0;
        try(ZipFile before=new ZipFile(source.toFile()); ZipFile after=new ZipFile(output.toFile())) {
            Set<String> expected=new HashSet<>();
            Enumeration<? extends ZipEntry> entries=before.entries();
            while(entries.hasMoreElements()) {
                ZipEntry entry=entries.nextElement(); String name=entry.getName();
                if(name.startsWith("META-INF/") || name.equals(MODULE)) continue;
                expected.add(name); ZipEntry updated=after.getEntry(name);
                if(updated==null || !digest(before.getInputStream(entry)).equals(digest(after.getInputStream(updated))))
                    throw new SecurityException("Unexpected payload change: "+name);
                preserved++;
            }
            entries=after.entries();
            while(entries.hasMoreElements()) {
                String name=entries.nextElement().getName();
                if(!name.startsWith("META-INF/") && !name.equals(MODULE) && !expected.contains(name))
                    throw new SecurityException("Unexpected new payload: "+name);
            }
            if(!digest(after.getInputStream(after.getEntry(MODULE))).equals(digest(Files.newInputStream(Paths.get(args[2])))))
                throw new SecurityException("Embedded module differs from verified APK");
        }
        System.out.println("PASS signing certificate matches installed QQ");
        System.out.println("PASS all "+preserved+" other ZIP payloads unchanged (including manifest, original QQ, loader and other modules)");
        System.out.println("PASS only target embedded module replaced");
        System.out.println("Candidate: "+output);
    }
}
