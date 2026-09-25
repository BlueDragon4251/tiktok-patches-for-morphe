// Run only on a SHA-verified fixture and a successful full catalog report.
// java -Xmx6g --class-path "<compiled patches>:<morphe API>:<gson>:<Kotlin stdlib>" scripts/tiktok/CaptureFixtureContracts.java APK REPORT OUTPUT
import app.morphe.patches.tiktok.shared.discovery.FixtureContracts;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

class CaptureFixtureContracts {
    static final String SCREEN_CAPTURE_REGISTER = "Landroid/app/Activity;->registerScreenCaptureCallback(Ljava/util/concurrent/Executor;Landroid/app/Activity$ScreenCaptureCallback;)V";
    static final String SCREEN_CAPTURE_UNREGISTER = "Landroid/app/Activity;->unregisterScreenCaptureCallback(Landroid/app/Activity$ScreenCaptureCallback;)V";
    static String digest(Path file) throws Exception {
        try (InputStream in = Files.newInputStream(file)) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("APK REPORT OUTPUT required");
        Path apk = Path.of(args[0]), reportFile = Path.of(args[1]), output = Path.of(args[2]);
        JsonObject report = JsonParser.parseString(Files.readString(reportFile)).getAsJsonObject();
        String actualSha = digest(apk);
        if (!actualSha.equals(report.get("fixtureSha256").getAsString())) throw new IllegalArgumentException("APK SHA differs from full catalog report");
        if (report.get("schema").getAsInt() != 2) throw new IllegalArgumentException("Expected schema 2 hook report");
        Set<String> wanted = new HashSet<>();
        for (JsonElement element : report.getAsJsonArray("fingerprints")) {
            JsonObject hook = element.getAsJsonObject();
            if (hook.has("status") && !hook.get("status").getAsString().equals("resolved") && hook.get("required").getAsBoolean())
                throw new IllegalArgumentException("Required hook unresolved: " + hook.get("hook"));
            if (!hook.has("origin") || !hook.get("origin").getAsString().equals("apk")) continue;
            StringBuilder descriptor = new StringBuilder(hook.get("owner").getAsString()).append("->").append(hook.get("name").getAsString()).append('(');
            for (JsonElement parameter : hook.getAsJsonArray("parameters")) {
                if (parameter.isJsonObject()) descriptor.append(parameter.getAsJsonObject().get("type").getAsString());
                else descriptor.append(parameter.getAsString());
            }
            descriptor.append(')').append(hook.get("returns").getAsString());
            wanted.add(descriptor.toString());
        }
        TreeMap<String,String> methods = new TreeMap<>(), classes = new TreeMap<>();
        Set<String> allSite = new TreeSet<>();
        MultiDexContainer<?> container = DexFileFactory.loadDexContainer(apk.toFile(), Opcodes.getDefault());
        for (String dex : container.getDexEntryNames()) {
            var entry = container.getEntry(dex);
            if (entry == null) throw new IllegalArgumentException("Missing DEX entry " + dex);
            for (ClassDef owner : entry.getDexFile().getClasses()) {
                for (Method method : owner.getMethods()) {
                    boolean selected = wanted.contains(method.toString());
                    if (!selected && method.getImplementation() != null) {
                        for (var instruction : method.getImplementation().getInstructions()) {
                            if (instruction instanceof ReferenceInstruction reference && reference.getReference() instanceof MethodReference invoke) {
                                String call = invoke.toString();
                                if (call.equals(SCREEN_CAPTURE_REGISTER) || call.equals(SCREEN_CAPTURE_UNREGISTER)) {
                                    selected = true;
                                    allSite.add(method.toString());
                                    break;
                                }
                            }
                        }
                    }
                    if (selected) {
                        String key = method.toString();
                        if (methods.putIfAbsent(key, FixtureContracts.INSTANCE.signature(method)) != null)
                            throw new IllegalArgumentException("Duplicate original method " + key);
                        classes.putIfAbsent(owner.getType(), FixtureContracts.INSTANCE.classSignature(owner));
                    }
                }
            }
        }
        wanted.removeAll(methods.keySet());
        if (!wanted.isEmpty()) throw new IllegalArgumentException("Missing original hooks: " + wanted);
        JsonObject reviewed = new JsonObject();
        reviewed.addProperty("schema", 1);
        reviewed.addProperty("package", report.get("package").getAsString());
        reviewed.addProperty("version", report.get("version").getAsString());
        reviewed.addProperty("versionCode", report.get("versionCode").getAsLong());
        reviewed.addProperty("sha256", actualSha);
        reviewed.addProperty("provenance", "Verified 46.7.3 global APK; structural method/class digests from original DEX before injection. SHA locked to fixtures/tiktok/fixtures.json.");
        reviewed.add("allSiteFrameworkCalls", new Gson().toJsonTree(allSite));
        reviewed.add("methods", new Gson().toJsonTree(methods));
        reviewed.add("classes", new Gson().toJsonTree(classes));
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(reviewed));
        System.out.println("Captured " + methods.size() + " original methods, " + classes.size() + " classes, " + allSite.size() + " screen capture callers; SHA " + actualSha);
    }
}
