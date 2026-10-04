import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.TopLevelClassMapping;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.cadixdev.bombe.type.signature.FieldSignature;
import org.cadixdev.bombe.type.FieldType;
import org.cadixdev.bombe.type.MethodDescriptor;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;

import java.nio.file.*;
import java.util.*;

/**
 * yarn(1.21.11) -> intermediary -> mojang(1.21.11) 을 합쳐 Lorenz MappingSet 을 만들고 Mercury 로 소스를 리맵.
 * args: yarnTiny reverseTiny srcDir outDir classpath(:)...
 */
public class Remap26 {
    static class Tiny {
        Map<String, String> cls = new HashMap<>();          // inter -> named
        Map<String, String> clsRev = new HashMap<>();       // named -> inter
        // inter class -> (kind|interName|desc(named ns)) -> named
        Map<String, Map<String, String[]>> members = new HashMap<>(); // key: kind + "\t" + interName -> [named, desc]
    }

    static Tiny parse(Path p) throws Exception {
        Tiny t = new Tiny();
        String cur = null;
        for (String line : Files.readAllLines(p)) {
            if (line.startsWith("c\t")) {
                String[] a = line.split("\t");
                // tiny 2 0 named official intermediary
                cur = a[3];
                t.cls.put(a[3], a[1]);
                t.clsRev.put(a[1], a[3]);
            } else if (line.startsWith("\tm\t") || line.startsWith("\tf\t")) {
                String[] a = line.split("\t");
                // ["", kind, desc, named, official, inter]
                String kind = a[1];
                String desc = a[2];
                String named = a[3];
                String inter = a.length > 5 ? a[5] : a[4];
                t.members.computeIfAbsent(cur, k -> new HashMap<>()).put(kind + "\t" + inter, new String[]{named, desc});
            }
        }
        return t;
    }

    public static void main(String[] args) throws Exception {
        Tiny yarn = parse(Paths.get(args[0]));
        Tiny moj = parse(Paths.get(args[1]));
        Path src = Paths.get(args[2]);
        Path out = Paths.get(args[3]);
        MappingSet set = MappingSet.create();
        int nc = 0, nm = 0, nf = 0;
        for (Map.Entry<String, String> e : yarn.cls.entrySet()) {
            String inter = e.getKey();
            String yName = e.getValue();
            String mName = moj.cls.get(inter);
            if (mName == null) continue;
            ClassMapping<?, ?> cm = set.getOrCreateClassMapping(yName);
            cm.setDeobfuscatedName(mName.contains("$") ? mName.substring(mName.lastIndexOf('$') + 1) : mName);
            nc++;
            Map<String, String[]> ym = yarn.members.get(inter);
            Map<String, String[]> mm = moj.members.get(inter);
            if (ym == null || mm == null) continue;
            for (Map.Entry<String, String[]> me : ym.entrySet()) {
                String[] mo = mm.get(me.getKey());
                if (mo == null) continue;
                String yn = me.getValue()[0];
                String desc = me.getValue()[1];
                String mn = mo[0];
                if (mn.equals(yn)) continue;
                if (mn.startsWith("method_") || mn.startsWith("field_")) continue;
                if (me.getKey().startsWith("m\t")) {
                    cm.getOrCreateMethodMapping(MethodSignature.of(yn, desc)).setDeobfuscatedName(mn);
                    nm++;
                } else {
                    cm.getOrCreateFieldMapping(FieldSignature.of(yn, desc)).setDeobfuscatedName(mn);
                    nf++;
                }
            }
        }
        System.out.println("classes " + nc + " methods " + nm + " fields " + nf);
        Mercury mercury = new Mercury();
        for (int i = 4; i < args.length; i++) {
            for (String cp : args[i].split(":")) {
                if (!cp.isEmpty()) mercury.getClassPath().add(Paths.get(cp));
            }
        }
        mercury.setGracefulClasspathChecks(true);
        mercury.setSourceCompatibilityFromRelease(21);
        mercury.setFlexibleAnonymousClassMemberLookups(true);
        mercury.setGracefulJavadocClasspathChecks(true);
        mercury.getProcessors().add(MercuryRemapper.create(set));
        mercury.rewrite(src, out);
        System.out.println("done");
    }
}
