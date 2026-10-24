import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.com.intellij.openapi.Disposable;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.com.intellij.psi.PsiElement;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.config.CommonConfigurationKeys;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.kdoc.psi.api.KDoc;
import org.jetbrains.kotlin.kdoc.psi.impl.KDocLink;
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.KtCallableDeclaration;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtConstructor;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtEnumEntry;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtModifierListOwner;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtNullableType;
import org.jetbrains.kotlin.psi.KtObjectDeclaration;
import org.jetbrains.kotlin.psi.KtParameter;
import org.jetbrains.kotlin.psi.KtProperty;
import org.jetbrains.kotlin.psi.KtPropertyAccessor;
import org.jetbrains.kotlin.psi.KtPsiFactory;
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry;
import org.jetbrains.kotlin.psi.KtTypeAlias;
import org.jetbrains.kotlin.psi.KtTypeElement;
import org.jetbrains.kotlin.psi.KtTypeParameter;
import org.jetbrains.kotlin.psi.KtTypeReference;
import org.jetbrains.kotlin.psi.KtUserType;

/**
 * Scores every -api module against the mechanical checks M1-M8. See README.
 *
 * Parses with the Kotlin compiler's own PSI rather than text matching, so visibility, KDoc placement and
 * signature types are read exactly as the compiler sees them and the same source always gives the same score.
 * Types are matched by simple name only: there is no symbol resolution, so an import alias would hide a match.
 *
 * Run through score.sh, which finds the Kotlin compiler jars in the Gradle cache and passes them on the classpath.
 */
public class Scorer {

    static final String TOGGLE_OWNER = "feature-toggles-api";
    static final String SIZE_EXCLUDED_FROM_FLOOR = "browser-api";
    static final double KDOC_THRESHOLD = 0.80;
    static final int SIZE_CEILING = 80;
    static final List<String> IMPL_SUFFIXES = List.of("Repository", "Dao", "Entity", "ViewModel", "Impl");
    static final Set<String> NULLABLE_FACTORY_TYPES = Set.of("Intent", "Fragment", "View", "Bundle");
    static final Set<String> UNTYPED_TYPES = Set.of("Any", "Pair", "Triple");
    static final String[] RULES = {"M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8"};

    enum Kind { TYPE, TOP_FUNCTION, TOP_PROPERTY, MEMBER_FUNCTION, MEMBER_PROPERTY }

    record Decl(String module, String file, int line, String name, Kind kind, boolean documented) {
        boolean countsForM3() {
            return kind == Kind.TYPE || kind == Kind.TOP_FUNCTION;
        }
    }

    record Finding(String module, String rule, String file, int line, String declaration, String detail) {}

    /** Every constructor of a public class, whatever its visibility, so a comparison can tell one exists. Not counted. */
    record Ctor(String file, int line, String name, boolean isPublic) {}

    static class Module {
        final String name;
        final Path dir;
        final List<Decl> decls = new ArrayList<>();
        final List<Finding> findings = new ArrayList<>();
        final List<Ctor> constructors = new ArrayList<>();
        final Set<String> failing = new TreeSet<>();
        double topKdoc;
        double memberKdoc;

        Module(String name, Path dir) {
            this.name = name;
            this.dir = dir;
        }

        int score() {
            return RULES.length - failing.size();
        }
    }

    static Path repoRoot;
    static KtPsiFactory psiFactory;

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Usage: Scorer <repoRoot> <outDir> <date> [previousScoresCsv]");
            System.exit(1);
        }
        repoRoot = Path.of(args[0]).toAbsolutePath().normalize();
        Path outDir = Path.of(args[1]).toAbsolutePath().normalize();
        String date = args[2];
        Path previous = args.length > 3 ? Path.of(args[3]) : null;

        Disposable disposable = Disposer.newDisposable();
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.Companion.getNONE());
        KotlinCoreEnvironment environment = KotlinCoreEnvironment.createForProduction(
            disposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES);
        psiFactory = new KtPsiFactory(environment.getProject(), false);

        List<Module> modules = findModules();
        for (Module module : modules) {
            scan(module);
            evaluate(module);
        }
        Disposer.dispose(disposable);

        Files.createDirectories(outDir);
        writeScoresCsv(modules, outDir.resolve("api-quality-scores-" + date + ".csv"));
        writeFindingsCsv(modules, outDir.resolve("api-quality-findings-" + date + ".csv"));
        writeDeclarationsCsv(modules, outDir.resolve("api-quality-declarations-" + date + ".csv"));
        writeConstructorsCsv(modules, outDir.resolve("api-quality-constructors-" + date + ".csv"));
        writeScorecard(modules, date, previous, outDir.resolve("api-quality-scorecard-" + date + ".md"));
    }

    // ---- discovery ----

    static final List<String> skippedModules = new ArrayList<>();

    static List<Module> findModules() throws IOException {
        List<Module> modules = new ArrayList<>();
        try (Stream<Path> dirs = Files.walk(repoRoot, 2)) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                String name = dir.getFileName().toString();
                if (!name.endsWith("-api") || dir.toString().contains("/build/") || dir.toString().contains("/.git/")) continue;
                if (!Files.isRegularFile(dir.resolve("build.gradle")) && !Files.isRegularFile(dir.resolve("build.gradle.kts"))) continue;
                if (!Files.isDirectory(dir.resolve("src/main"))) {
                    skippedModules.add(name);
                    continue;
                }
                modules.add(new Module(name, dir));
            }
        }
        return modules;
    }

    static void scan(Module module) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(module.dir.resolve("src/main"))) {
            files = walk.filter(p -> p.toString().endsWith(".kt")).sorted().toList();
        }
        for (Path path : files) {
            String text = Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
            KtFile file = psiFactory.createFile(path.getFileName().toString(), text);
            String rel = repoRoot.relativize(path).toString();
            for (KtDeclaration d : file.getDeclarations()) {
                visitTopLevel(module, rel, file, d);
            }
        }
    }

    // ---- public surface ----

    static boolean isPublic(KtModifierListOwner owner) {
        return !owner.hasModifier(KtTokens.PRIVATE_KEYWORD)
            && !owner.hasModifier(KtTokens.INTERNAL_KEYWORD)
            && !owner.hasModifier(KtTokens.PROTECTED_KEYWORD);
    }

    static boolean isOverride(KtModifierListOwner owner) {
        return owner.hasModifier(KtTokens.OVERRIDE_KEYWORD);
    }

    static void visitTopLevel(Module module, String rel, KtFile file, KtDeclaration d) {
        if (!isPublic(d)) return;
        if (d instanceof KtClassOrObject type) {
            visitType(module, rel, file, type, "");
        } else if (d instanceof KtTypeAlias alias) {
            addDecl(module, rel, file, alias, alias.getName(), Kind.TYPE, alias.getDocComment() != null);
            checkTypeName(module, rel, file, alias, alias.getName());
            checkTypeReference(module, rel, file, alias, alias.getName(), alias.getTypeReference(), false);
        } else if (d instanceof KtNamedFunction fn) {
            addDecl(module, rel, file, fn, signature(fn, ""), Kind.TOP_FUNCTION, fn.getDocComment() != null);
            checkCallable(module, rel, file, fn, signature(fn, ""));
        } else if (d instanceof KtProperty prop) {
            String pname = propertyName(prop, "");
            addDecl(module, rel, file, prop, pname, Kind.TOP_PROPERTY, prop.getDocComment() != null);
            checkCallable(module, rel, file, prop, pname);
            checkVar(module, rel, file, prop, pname);
        }
    }

    static void visitType(Module module, String rel, KtFile file, KtClassOrObject type, String prefix) {
        boolean companion = type instanceof KtObjectDeclaration obj && obj.isCompanion();
        String name = companion ? prefix + "Companion" : prefix + type.getName();
        if (!companion) {
            addDecl(module, rel, file, type, name, Kind.TYPE, type.getDocComment() != null);
            checkTypeName(module, rel, file, type, type.getName());
            for (KtSuperTypeListEntry entry : type.getSuperTypeListEntries()) {
                checkTypeReference(module, rel, file, type, name, entry.getTypeReference(), false);
            }
            for (KtTypeParameter tp : type.getTypeParameters()) {
                checkTypeReference(module, rel, file, type, name, tp.getExtendsBound(), false);
            }
        }
        String memberPrefix = name + ".";
        KDoc classDoc = type.getDocComment();

        if (type instanceof KtClass cls) {
            // A val/var parameter is a public property even when the constructor itself is private.
            for (KtParameter param : cls.getPrimaryConstructorParameters()) {
                if (!param.hasValOrVar() || !isPublic(param) || isOverride(param)) continue;
                String pname = memberPrefix + param.getName();
                addDecl(module, rel, file, param, pname, Kind.MEMBER_PROPERTY,
                    param.getDocComment() != null || documentsParameter(classDoc, param.getName()));
                checkTypeReference(module, rel, file, param, pname, param.getTypeReference(), false);
                if (param.isMutable()) {
                    addFinding(module, "M8", rel, file, param, pname, "public var");
                }
            }
            if (cls.getPrimaryConstructor() != null) {
                checkConstructor(module, rel, file, cls, cls.getPrimaryConstructor(), memberPrefix);
            }
        }

        for (KtDeclaration member : type.getDeclarations()) {
            if (member instanceof KtConstructor<?> ctor && type instanceof KtClass cls) {
                checkConstructor(module, rel, file, cls, ctor, memberPrefix);
                continue;
            }
            if (!isPublic(member) || member instanceof KtEnumEntry) continue;
            if (member instanceof KtClassOrObject nested) {
                visitType(module, rel, file, nested, memberPrefix);
            } else if (member instanceof KtTypeAlias alias) {
                addDecl(module, rel, file, alias, memberPrefix + alias.getName(), Kind.TYPE, alias.getDocComment() != null);
            } else if (member instanceof KtNamedFunction fn) {
                if (isOverride(fn)) continue;
                String fname = signature(fn, memberPrefix);
                addDecl(module, rel, file, fn, fname, Kind.MEMBER_FUNCTION, fn.getDocComment() != null);
                checkCallable(module, rel, file, fn, fname);
            } else if (member instanceof KtProperty prop) {
                if (isOverride(prop)) continue;
                String pname = propertyName(prop, memberPrefix);
                addDecl(module, rel, file, prop, pname, Kind.MEMBER_PROPERTY,
                    prop.getDocComment() != null || documentsParameter(classDoc, prop.getName()));
                checkCallable(module, rel, file, prop, pname);
                checkVar(module, rel, file, prop, pname);
            }
        }
    }

    /** A property is documented by its type's KDoc through an `@property`/`@param` tag or a `[name]` link. */
    static boolean documentsParameter(KDoc doc, String name) {
        if (doc == null || name == null) return false;
        for (KDocTag tag : PsiTreeUtil.findChildrenOfType(doc, KDocTag.class)) {
            String tagName = tag.getName();
            if (("property".equals(tagName) || "param".equals(tagName)) && name.equals(tag.getSubjectName())) {
                return true;
            }
        }
        for (KDocLink link : PsiTreeUtil.findChildrenOfType(doc, KDocLink.class)) {
            if (link.getTagIfSubject() == null && name.equals(link.getLinkText())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Code as written can span lines and vary in spacing; names and details are identities in the comparison and cells in
     * the CSVs and reports, so `List<\n  Any\n>` and `List<Any>` must come out the same, on one line.
     */
    static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").replaceAll("([<(]) ", "$1").replaceAll(" ([>),])", "$1").replaceAll(",(?! )", ", ");
    }

    /** An extension property carries its receiver, like a function, so `String.value` and `Int.value` count differently. */
    static String propertyName(KtProperty prop, String prefix) {
        String receiver = prop.getReceiverTypeReference() != null ? prop.getReceiverTypeReference().getText() + "." : "";
        return oneLine(prefix + receiver + prop.getName());
    }

    static String signature(KtNamedFunction fn, String prefix) {
        String receiver = fn.getReceiverTypeReference() != null ? fn.getReceiverTypeReference().getText() + "." : "";
        String params = fn.getValueParameters().stream()
            .map(p -> p.getTypeReference() != null ? p.getTypeReference().getText() : "?")
            .collect(Collectors.joining(", "));
        return oneLine(prefix + receiver + fn.getName() + "(" + params + ")");
    }

    // ---- checks ----

    static void checkTypeName(Module module, String rel, KtFile file, PsiElement element, String name) {
        if (name == null) return;
        for (String suffix : IMPL_SUFFIXES) {
            if (name.endsWith(suffix)) {
                addFinding(module, "M1", rel, file, element, name, "type name ends in " + suffix);
                return;
            }
        }
    }

    static void checkCallable(Module module, String rel, KtFile file, KtCallableDeclaration callable, String name) {
        boolean untypedExempt = callable instanceof KtNamedFunction fn
            && ("readResolve".equals(fn.getName()) || ("equals".equals(fn.getName()) && fn.getValueParameters().size() == 1));
        checkTypeReference(module, rel, file, callable, name, callable.getTypeReference(), untypedExempt);
        checkTypeReference(module, rel, file, callable, name, callable.getReceiverTypeReference(), untypedExempt);
        for (KtParameter param : callable.getValueParameters()) {
            checkTypeReference(module, rel, file, callable, name, param.getTypeReference(), untypedExempt);
        }
        for (KtTypeParameter tp : callable.getTypeParameters()) {
            checkTypeReference(module, rel, file, callable, name, tp.getExtendsBound(), untypedExempt);
        }
        if (callable instanceof KtNamedFunction fn && fn.getTypeReference() != null) {
            KtTypeElement returned = fn.getTypeReference().getTypeElement();
            if (returned instanceof KtNullableType nullable && nullable.getInnerType() instanceof KtUserType user
                && NULLABLE_FACTORY_TYPES.contains(user.getReferencedName())) {
                addFinding(module, "M5", rel, file, fn, name, "returns " + user.getReferencedName() + "?");
            }
        }
    }

    /**
     * Applies M2, M7 and M8 to the parameters of a public constructor. A public val/var parameter is left to its property;
     * a private or overriding one isn't checked as a property, but callers still pass it. Enum and sealed class
     * constructors can't be called from outside, so they're skipped.
     */
    static void checkConstructor(Module module, String rel, KtFile file, KtClass cls, KtConstructor<?> ctor, String prefix) {
        String params = ctor.getValueParameters().stream()
            .map(p -> p.getTypeReference() != null ? p.getTypeReference().getText() : "?")
            .collect(Collectors.joining(", "));
        String name = oneLine(prefix + "constructor(" + params + ")");
        module.constructors.add(new Ctor(rel, line(file, ctor), name, isPublic(ctor)));
        if (!isPublic(ctor) || cls.isEnum() || cls.isSealed()) return;
        for (KtParameter param : ctor.getValueParameters()) {
            if (param.hasValOrVar() && isPublic(param) && !isOverride(param)) continue;
            checkTypeReference(module, rel, file, ctor, name, param.getTypeReference(), false);
        }
    }

    static void checkVar(Module module, String rel, KtFile file, KtProperty prop, String name) {
        if (!prop.isVar()) return;
        KtPropertyAccessor setter = prop.getSetter();
        if (setter != null && !isPublic(setter)) return;
        addFinding(module, "M8", rel, file, prop, name, "public var");
    }

    /** Applies M2, M7 and M8 to every type named in one type reference, including generic arguments. */
    static void checkTypeReference(
        Module module, String rel, KtFile file, PsiElement owner, String name, KtTypeReference ref, boolean untypedExempt) {
        if (ref == null) return;
        String type = oneLine(ref.getText());
        for (KtUserType user : PsiTreeUtil.collectElementsOfType(ref, KtUserType.class)) {
            if (user.getParent() instanceof KtUserType) continue; // qualifier of a longer name, e.g. Toggle in Toggle.State
            String simple = user.getReferencedName();
            if (simple == null) continue;
            if ("Toggle".equals(simple) && !module.name.equals(TOGGLE_OWNER)) {
                addFinding(module, "M2", rel, file, owner, name, "Toggle in " + type);
            }
            if (!untypedExempt && UNTYPED_TYPES.contains(simple)) {
                addFinding(module, "M7", rel, file, owner, name, simple + " in " + type);
            }
            if (simple.startsWith("Mutable") && simple.length() > 7 && Character.isUpperCase(simple.charAt(7))) {
                addFinding(module, "M8", rel, file, owner, name, simple + " in " + type);
            }
        }
    }

    static void addDecl(Module module, String rel, KtFile file, PsiElement element, String name, Kind kind, boolean documented) {
        module.decls.add(new Decl(module.name, rel, line(file, element), name, kind, documented));
    }

    static void addFinding(Module module, String rule, String rel, KtFile file, PsiElement element, String name, String detail) {
        module.findings.add(new Finding(module.name, rule, rel, line(file, element), name, detail));
    }

    static int line(KtFile file, PsiElement element) {
        PsiElement anchor = element instanceof KtDeclaration d && d.getDocComment() != null
            ? d.getDocComment().getNextSibling() : element;
        int offset = anchor.getTextRange().getStartOffset();
        String text = file.getText();
        while (offset < text.length() && Character.isWhitespace(text.charAt(offset))) offset++;
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    static void evaluate(Module module) {
        List<Decl> top = module.decls.stream().filter(Decl::countsForM3).toList();
        List<Decl> members = module.decls.stream().filter(d -> !d.countsForM3()).toList();
        module.topKdoc = ratio(top);
        module.memberKdoc = ratio(members);
        for (Decl d : module.decls) {
            if (!d.documented()) {
                module.findings.add(new Finding(module.name, d.countsForM3() ? "M3" : "M4", d.file(), d.line(), d.name(), "no KDoc"));
            }
        }
        if (module.decls.size() > SIZE_CEILING) {
            module.findings.add(new Finding(module.name, "M6", "", 0, module.name,
                module.decls.size() + " public declarations (ceiling " + SIZE_CEILING + ")"));
        }
        for (Finding f : module.findings) {
            if (f.rule().equals("M3") && module.topKdoc >= KDOC_THRESHOLD) continue;
            if (f.rule().equals("M4") && module.memberKdoc >= KDOC_THRESHOLD) continue;
            module.failing.add(f.rule());
        }
        module.findings.sort(FINDING_ORDER);
    }

    static final Comparator<Finding> FINDING_ORDER = Comparator.comparing(Finding::rule)
        .thenComparing(Finding::file).thenComparingInt(Finding::line).thenComparing(Finding::declaration)
        .thenComparing(Finding::detail);

    static double ratio(List<Decl> decls) {
        if (decls.isEmpty()) return 1.0;
        return decls.stream().filter(Decl::documented).count() / (double) decls.size();
    }

    // ---- output ----

    static List<Module> ranked(List<Module> modules) {
        return modules.stream()
            .sorted(Comparator.comparingInt(Module::score).thenComparing(m -> -m.decls.size()).thenComparing(m -> m.name))
            .toList();
    }

    static String pct(double value) {
        return Math.round(value * 100) + "%";
    }

    static String pct1(double value) {
        return String.format("%.1f%%", value * 100);
    }

    static String csv(String value) {
        boolean quote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        return quote ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    static void writeScoresCsv(List<Module> modules, Path out) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            w.println("module,score,declarations,top_kdoc,member_kdoc,failing");
            for (Module m : ranked(modules)) {
                w.println(String.join(",", m.name, String.valueOf(m.score()), String.valueOf(m.decls.size()),
                    pct(m.topKdoc), pct(m.memberKdoc), csv(String.join(" ", m.failing))));
            }
        }
    }

    static void writeFindingsCsv(List<Module> modules, Path out) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            w.println("module,rule,file,line,declaration,detail");
            for (Module m : modules) {
                for (Finding f : m.findings) {
                    w.println(String.join(",", csv(m.name), f.rule(), csv(f.file()), String.valueOf(f.line()), csv(f.declaration()), csv(f.detail())));
                }
            }
        }
    }

    static void writeDeclarationsCsv(List<Module> modules, Path out) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            w.println("module,kind,documented,file,line,declaration");
            for (Module m : modules) {
                for (Decl d : m.decls) {
                    w.println(String.join(",", csv(m.name), d.kind().name(), String.valueOf(d.documented()),
                        csv(d.file()), String.valueOf(d.line()), csv(d.name())));
                }
            }
        }
    }

    static void writeConstructorsCsv(List<Module> modules, Path out) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            w.println("module,public,file,line,declaration");
            for (Module m : modules) {
                for (Ctor c : m.constructors) {
                    w.println(String.join(",", csv(m.name), String.valueOf(c.isPublic()), csv(c.file()),
                        String.valueOf(c.line()), csv(c.name())));
                }
            }
        }
    }

    static Map<String, Integer> readPreviousScores(Path previous) throws IOException {
        Map<String, Integer> scores = new TreeMap<>();
        if (previous == null || !Files.exists(previous)) return scores;
        for (String row : Files.readAllLines(previous).stream().skip(1).toList()) {
            String[] cols = row.split(",");
            scores.put(cols[0], Integer.parseInt(cols[1]));
        }
        return scores;
    }

    static void writeScorecard(List<Module> modules, String date, Path previous, Path out) throws IOException {
        List<Module> rankedModules = ranked(modules);
        int totalDecls = modules.stream().mapToInt(m -> m.decls.size()).sum();
        int points = modules.stream().mapToInt(Module::score).sum();
        double unweighted = modules.stream().mapToDouble(m -> m.score() / 8.0).average().orElse(0);
        double weighted = modules.stream().mapToDouble(m -> m.score() / 8.0 * m.decls.size()).sum() / totalDecls;
        int floor = modules.stream().filter(m -> !m.name.equals(SIZE_EXCLUDED_FROM_FLOOR)).mapToInt(Module::score).min().orElse(0);
        String floorModules = modules.stream()
            .filter(m -> !m.name.equals(SIZE_EXCLUDED_FROM_FLOOR) && m.score() == floor)
            .map(m -> "`" + m.name + "`").collect(Collectors.joining(", "));

        StringBuilder md = new StringBuilder();
        md.append("# API Quality Scorecard, ").append(date).append("\n\n");
        md.append("Scored commit `").append(gitHead()).append("`\n\n");

        md.append("## Summary\n\n| | |\n|---|---|\n");
        md.append("| Modules scored | ").append(modules.size()).append(" (").append(skippedModules.size())
            .append(skippedModules.size() == 1 ? " has" : " have").append(" no source: ").append(skippedModules.stream().map(s -> "`" + s + "`").collect(Collectors.joining(", ")))
            .append(") |\n");
        md.append("| Points | ").append(points).append(" / ").append(modules.size() * 8).append(" |\n");
        md.append("| **Weighted average (by declaration count)** | **").append(pct1(weighted)).append("** |\n");
        md.append("| **Unweighted average** | **").append(pct1(unweighted)).append("** |\n");
        md.append("| **Minimum score, excluding `browser-api`** | **").append(floor).append("/8** (").append(floorModules).append(") |\n");
        md.append("| Public declarations | ").append(String.format("%,d", totalDecls)).append(" |\n");
        md.append("| Modules at 8/8 | ").append(modules.stream().filter(m -> m.score() == 8).count()).append(" |\n\n");

        md.append("### Modules failing each check\n\n| Check | Modules failing |\n|---|---|\n");
        for (String rule : RULES) {
            md.append("| ").append(rule).append(" | ").append(modules.stream().filter(m -> m.failing.contains(rule)).count()).append(" |\n");
        }

        Map<String, Integer> prev = readPreviousScores(previous);
        md.append("\n## Scores\n\n| Module | Score | Decls | Failing |\n|---|---|---|---|\n");
        for (Module m : rankedModules) {
            md.append("| `").append(m.name).append("` | ").append(m.score()).append("/8 | ").append(m.decls.size()).append(" | ")
                .append(m.failing.isEmpty() ? "none" : String.join(", ", m.failing)).append(" |\n");
        }

        if (!prev.isEmpty()) {
            md.append("\n## Score changes since the previous scorecard\n\n| Module | Before | Now |\n|---|---|---|\n");
            for (Module m : rankedModules) {
                Integer before = prev.get(m.name);
                if (before == null) {
                    md.append("| `").append(m.name).append("` | new | ").append(m.score()).append("/8 |\n");
                } else if (before != m.score()) {
                    md.append("| `").append(m.name).append("` | ").append(before).append("/8 | ").append(m.score()).append("/8 |\n");
                }
            }
        }

        Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
    }

    static String gitHead() {
        try {
            Process p = new ProcessBuilder("git", "-C", repoRoot.toString(), "rev-parse", "--short=10", "HEAD").start();
            String head = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return head.isEmpty() ? "unknown" : head;
        } catch (IOException e) {
            return "unknown";
        }
    }
}
