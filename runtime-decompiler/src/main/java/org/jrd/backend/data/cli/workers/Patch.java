package org.jrd.backend.data.cli.workers;

import com.github.difflib.patch.PatchFailedException;

import org.jrd.backend.communication.ErrorCandidate;
import org.jrd.backend.communication.FsAgent;
import org.jrd.backend.core.AgentRequestAction;
import org.jrd.backend.data.VmInfo;
import org.jrd.backend.data.VmManager;
import org.jrd.backend.data.cli.utils.BytecodeSorter;
import org.jrd.backend.data.cli.CliUtils;
import org.jrd.backend.data.cli.utils.CompileArguments;
import org.jrd.backend.data.cli.Help;
import org.jrd.backend.data.cli.Lib;
import org.jrd.backend.data.cli.utils.FqnAndClassToJar;
import org.jrd.backend.data.cli.utils.ObtainedCodeWithNameAndBytecode;
import org.jrd.backend.data.cli.utils.PluginWrapperWithMetaInfo;
import org.jrd.backend.decompiling.PluginManager;
import org.jrd.frontend.frame.main.decompilerview.DecompilationController;
import org.jrd.frontend.frame.main.decompilerview.HexWithControls;
import org.jrd.frontend.frame.main.popup.DiffPopup;
import org.jrd.frontend.frame.main.popup.SingleFilePatch;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.mkoncek.classpathless.api.ClassIdentifier;
import io.github.mkoncek.classpathless.api.IdentifiedBytecode;
import io.github.mkoncek.classpathless.api.IdentifiedSource;

public class Patch {

    private final List<String> filteredArgs;
    private final VmManager vmManager;
    private final PluginManager pluginManager;
    private final boolean isHex;
    private final boolean isVerbose;
    private final boolean isRevert;
    private final boolean isBoot;

    public Patch(
            boolean isHex, boolean isVerbose, List<String> filteredArgs, boolean isRevert, VmManager vmManager,
            PluginManager pluginManager, boolean isBoot
    ) {
        this.filteredArgs = filteredArgs;
        this.isRevert = isRevert;
        this.vmManager = vmManager;
        this.pluginManager = pluginManager;
        this.isHex = isHex;
        this.isVerbose = isVerbose;
        this.isBoot = isBoot;
    }

    /* FIXME refactor
     * The refactoring should be simple, there are obvious parts like check all , init all, gather all, compile all, upload all...
     */
    @SuppressWarnings({"MethodLength", "CyclomaticComplexity", "ExecutableStatementCount", "JavaNCSS", "UnnecessaryParentheses"})
    public VmInfo patch() throws Exception {
        //--patch <puc>  ((plugin)xor(SP/CP)( (-hex) (-R) < patch
        String puc;
        VmInfo vmInfo;
        String pluginXorPath;
        if (isHex) {
            if (filteredArgs.size() == 2) {
                puc = filteredArgs.get(1);
                vmInfo = CliUtils.getVmInfo(puc, vmManager);
                pluginXorPath = "nothingNowhere";
            } else if (filteredArgs.size() == 3) {
                puc = filteredArgs.get(1);
                vmInfo = CliUtils.getVmInfo(puc, vmManager);
                pluginXorPath = filteredArgs.get(2);
            } else {
                throw new IllegalArgumentException("Incorrect argument count! Please use '" + Help.PATCH_FORMAT + "'.");
            }
        } else {
            if (filteredArgs.size() != 3) {
                throw new IllegalArgumentException("Incorrect argument count! Please use '" + Help.PATCH_FORMAT + "'.");
            }
            puc = filteredArgs.get(1);
            vmInfo = CliUtils.getVmInfo(puc, vmManager);
            pluginXorPath = filteredArgs.get(2);
        }
        List<String> patch = DecompilationController.stdinToStrings();
        List<SingleFilePatch> files = DiffPopup.getIndividualPatches(patch);
        for (int i = 0; i < files.size(); i++) {
            SingleFilePatch startEnd = files.get(i);
            /**
             * todo, compile the +++ b/runtime-deco
             * with biggest group? By default?
             * new agent api to get more info from runniong vm?
             *
             * never to defaul, default is just wrong.
             * add new files to the default group, and MERGE whole default group to the BIGEST other group
             */
            String header1 = patch.get(startEnd.getStart());
            String header2 = patch.get(startEnd.getStart() + 1);
            String className = DiffPopup.parseClassFromHeader(header1);
            String classNameCheck = DiffPopup.parseClassFromHeader(header2);
            if (!className.equals(classNameCheck)) {
                if ((!isRevert && DiffPopup.isRemoveFile(header1) && DiffPopup.isAddDevNull(header2)) ||
                        (isRevert && DiffPopup.isAddFile(header2) && DiffPopup.isRemoveDevNull(header1))) {
                    System.out.println(
                            "Removal of files can not be supported. Jvm can not unload: \n" + patch.get(startEnd.getStart()) + "\n" +
                                    patch.get(startEnd.getStart() + 1) + " removing from set; " + (isRevert ? "\nrevert on" : "")
                    );
                    files.remove(i);
                    i--;
                    continue;
                }
                if ((!isRevert && DiffPopup.isAddFile(header2) && DiffPopup.isRemoveDevNull(header1)) ||
                        (isRevert && DiffPopup.isRemoveFile(header1) && DiffPopup.isAddDevNull(header2))) {
                    System.out.println("Detected addition of file:");
                    if (DiffPopup.isDevNull((header2))) {
                        System.out.println("  " + className);
                        //leaving as is, header1 is file, header2 is dev null
                    } else {
                        System.out.println("  " + classNameCheck);
                        patch.set(startEnd.getStart(), "+++ forged/" + classNameCheck);
                        patch.set(startEnd.getStart() + 1, "--- /dev/null");
                    }
                    continue; //we cant init it
                } else {
                    throw new RuntimeException(
                            "Invalid file header for:\n" + patch.get(startEnd.getStart()) + "\n" + patch.get(startEnd.getStart() + 1)
                    );
                }
            }
            Lib.initClass(vmInfo, vmManager, className, System.out);
        }
        if (files.isEmpty()) {
            throw new RuntimeException("Nothing left in to patch!");
        }
        FsAgent initialSearch;
        if (isHex) {
            initialSearch = FsAgent.createAdditionalClassPathFsAgent(pluginXorPath);
        } else {
            initialSearch = FsAgent.createAdditionalSourcePathFsAgent(pluginXorPath);
        }

        List<String> local = new ArrayList<>();
        List<String> remote = new ArrayList<>();
        List<String> added = new ArrayList<>();

        List<ObtainedCodeWithNameAndBytecode> obtainedCodeWithNameAndBytecode = new ArrayList<>(files.size());
        Map<String, Integer> bytecodeLevelCache = new HashMap<>();
        for (SingleFilePatch startEnd : files) {
            String className = DiffPopup.parseClassFromHeader(patch.get(startEnd.getStart()));
            boolean isNew = DiffPopup.isDevNull(patch.get(startEnd.getStart() + 1));
            //todo, maybe repalce default group by scan from vm, eg for java.lang.Integer?
            //and thus have sane default?
            //fixme, can  be misleading, if we upload to NEWER jvm...
            Integer byteCodeLevel = null;
            if (isNew) {
                System.out.println("Creating " + className);
                added.add(className);
                obtainedCodeWithNameAndBytecode.add(new ObtainedCodeWithNameAndBytecode(className, Base64.getEncoder().encodeToString(new byte[0]), null));
                bytecodeLevelCache.put(className, byteCodeLevel);
            } else {
                System.out.println("Obtaining " + className);
                String base64Bytes = initialSearch.submitRequest(AgentRequestAction.RequestAction.BYTES + " " + className);
                ErrorCandidate errorCandidateLocal = new ErrorCandidate(base64Bytes);
                if (errorCandidateLocal.isError()) {
                    //not found on additional cp/sp: getting from puc
                    base64Bytes = Lib.obtainClass(vmInfo, className, vmManager).getLoadedClassBytes();
                    ErrorCandidate errorCandidateRemote = new ErrorCandidate(base64Bytes);
                    if (errorCandidateRemote.isError()) {
                        throw new RuntimeException(className + " not found on local nor remote paths/vm"); //not probable, see the init check above
                    }
                    byteCodeLevel = Lib.getBuildJavaPerVersion(Base64.getDecoder().decode(base64Bytes));
                    remote.add(className);
                    if (isHex) {
                        //we are done, we have just  by obtainClasses
                        System.out.println("...remote binary");
                    } else {
                        String decompiledSrc = decompileBytesByDecompilerName(base64Bytes, pluginXorPath, className, vmInfo);
                        base64Bytes = Base64.getEncoder().encodeToString(decompiledSrc.getBytes(StandardCharsets.UTF_8));
                        System.out.println("...remote decompiled src ( compiled as " + byteCodeLevel + ")");
                    }
                } else {
                    local.add(className);
                    if (isHex) {
                        //we are done, base64 bytes are already loaded by initialSearch
                        System.out.println("...local binary");
                    } else {
                        //we are done also here, it was found on SRC path (and is base64 as it is found by agent)
                        //but wee need to find how it was compiled
                        String remoteImpl = Lib.obtainClass(vmInfo, className, vmManager).getLoadedClassBytes();
                        ErrorCandidate errorCandidateRemote = new ErrorCandidate(remoteImpl);
                        if (errorCandidateRemote.isError()) {
                            throw new RuntimeException(className + " not found on local nor remote paths/vm"); //not probable, see the init check above
                        }
                        byteCodeLevel = Lib.getBuildJavaPerVersion(Base64.getDecoder().decode(remoteImpl));
                        System.out.println("...local src ( compiled as " + byteCodeLevel + ")");
                    }
                }
                obtainedCodeWithNameAndBytecode.add(new ObtainedCodeWithNameAndBytecode(className, base64Bytes, byteCodeLevel));
                bytecodeLevelCache.put(className, byteCodeLevel);
            }
        }
        if (obtainedCodeWithNameAndBytecode.size() == remote.size()) {
            System.out.println("Warning! All classes found only in remote vm!");
        } else if (obtainedCodeWithNameAndBytecode.size() == local.size()) {
            System.out.println("All classes found on local path");
        } else if (obtainedCodeWithNameAndBytecode.size() == (local.size() + remote.size())) {
            //this is theoretical, current input/output do nto allow inclusion of decompielr and src path together... maybe improvable later
            System.out.println("WARNING! Some (" + local.size() + ") classes found in local, some (" + remote.size() + ") in remote");
        }
        System.out.println(added.size() + " classes are new");

        //Dont forget, if one file is patched more times, then it is expected, that it is applied to the already patched!
        Map<String, String> patched = new HashMap(files.size());
        for (int i = 0; i < files.size(); i++) {
            SingleFilePatch startEnd = files.get(i);
            String className = DiffPopup.parseClassFromHeader(patch.get(startEnd.getStart()));
            ObtainedCodeWithNameAndBytecode nameBody = obtainedCodeWithNameAndBytecode.get(i);
            if (!nameBody.getName().equals(className)) {
                throw new RuntimeException("Misaligned patching of " + nameBody.getName() + " by " + className);
            }
            System.out.println("Patching " + nameBody.getName());
            String toPatch = nameBody.getBase64Body();
            if (patched.containsKey(className)) {
                toPatch = patched.get(className);
            }
            if (isHex) {
                byte[] bytes = Base64.getDecoder().decode(toPatch);
                List<String> hexLines = HexWithControls.bytesToStrings(bytes);
                if (isVerbose) {
                    System.out.println("-  " + hexLines.stream().collect(Collectors.joining("\n-  ")));
                }
                List<String> patchedLines = applySubPatch(patch, startEnd, hexLines);
                if (isVerbose) {
                    System.out.println("+  " + patchedLines.stream().collect(Collectors.joining("\n+  ")));
                }
                String patchedBase64 =
                        Base64.getEncoder().encodeToString(HexWithControls.hexToBytes(HexWithControls.hexLinesToHexString(patchedLines)));
                patched.put(className, patchedBase64);
                System.out.println("Patched bin");
            } else {
                String src = new String(Base64.getDecoder().decode(toPatch), StandardCharsets.UTF_8); //change to src
                List<String> srcLines = new ArrayList<>();
                if (!src.isEmpty()) {
                    srcLines = Arrays.asList(src.split("\n"));
                }
                if (isVerbose) {
                    System.out.println("-  " + srcLines.stream().collect(Collectors.joining("\n-  ")));
                }
                List<String> patchedLines = applySubPatch(patch, startEnd, srcLines);
                if (isVerbose) {
                    System.out.println("+  " + patchedLines.stream().collect(Collectors.joining("\n+  ")));
                }
                String patchedBase64 = Base64.getEncoder()
                        .encodeToString(patchedLines.stream().collect(Collectors.joining("\n")).getBytes(StandardCharsets.UTF_8));
                patched.put(className, patchedBase64);
                System.out.println("Patched src");
            }
        }

        //in addition, we have to different by bytecode version (although it is src only)
        Map<Integer, Map<String, String>> binariesToUpload = new HashMap<>(files.size());
        if (isHex) {
            BytecodeSorter bt = new BytecodeSorter.HexDummySorter(patched.entrySet());
            binariesToUpload = bt.sort();
        } else {
            System.out.println("Compiling patched sources.");
            CompileArguments args = new CompileArguments(pluginManager, vmManager, vmInfo, pluginXorPath);
            PluginWrapperWithMetaInfo wrapper = Lib.getPluginWrapper(pluginManager, pluginXorPath, false);
            List<Map.Entry<String, String>> patchedList = new ArrayList<>(patched.entrySet());
            for (Integer detectedByteCode : new HashSet<>(bytecodeLevelCache.values())) {
                List<IdentifiedSource> thisBytecodeClasses = patchedList.stream().filter(
                        a -> null == bytecodeLevelCache.get(a.getKey()) || detectedByteCode.equals(bytecodeLevelCache.get(a.getKey()))
                ).map(a -> new IdentifiedSource(new ClassIdentifier(a.getKey()), Base64.getDecoder().decode(a.getValue())))
                        .collect(Collectors.toList());
                //ternary operator is constantly killed by autoformater

                Map<String, String> compiledForSingleBytecodeLevel = new HashMap<>();
                Collection<IdentifiedBytecode> compiledFiles = Compile.compile(
                        args, thisBytecodeClasses.toArray(IdentifiedSource[]::new), wrapper, detectedByteCode, System.out, isVerbose
                );
                for (IdentifiedBytecode compiled : compiledFiles) {
                    compiledForSingleBytecodeLevel
                            .put(compiled.getClassIdentifier().getFullName(), Base64.getEncoder().encodeToString(compiled.getFile()));
                }
                binariesToUpload.put(detectedByteCode, compiledForSingleBytecodeLevel);
            }
        }

        List<String> failures = new ArrayList<>();
        List<String> passes = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, String>> toUploadWithBytecode : binariesToUpload.entrySet()) {
            Integer bytecodeLevel = toUploadWithBytecode.getKey();
            System.out.println("Upload group of bytecode level: " + (bytecodeLevel == null ? "default:" : "" + bytecodeLevel));
            for (Map.Entry<String, String> toUpload : toUploadWithBytecode.getValue().entrySet()) {
                String className = toUpload.getKey();
                String reply;
                boolean maybeNew = false;
                PrintStream defaultErr = System.err;
                try {
                    System.setErr(new PrintStream(new ByteArrayOutputStream())); //the init is to verbose in bellow
                    Lib.initClass(vmInfo, vmManager, className, new PrintStream(new ByteArrayOutputStream()));
                } catch (RuntimeException ex) {
                    //even if we would be counting new files during patching,
                    //there can be much more of them after compilation
                    maybeNew = true;
                } finally {
                    System.setErr(defaultErr);
                }
                if (maybeNew) {
                    System.out.println("Uploading new class: " + className);
                    String reply1 = Lib.addByteClassesViaJar(
                            vmInfo,
                            Arrays.asList(new IdentifiedBytecode[]{
                                    new IdentifiedBytecode(
                                            new ClassIdentifier(className),
                                            Base64.getDecoder().decode(toUpload.getValue()))}),
                            isBoot, vmManager);
                    ErrorCandidate ec1 = new ErrorCandidate(reply1); //unluckily always ok
                    try {
                        System.setErr(new PrintStream(new ByteArrayOutputStream()));
                        reply = Lib.initClassNoThrow(vmInfo, vmManager, className);
                    } finally {
                        System.setErr(defaultErr);
                    }
                } else {
                    System.out.println("Overwriting class: " + className);
                    reply = Lib.uploadClass(vmInfo, className, toUpload.getValue(), vmManager);
                }
                ErrorCandidate ec = new ErrorCandidate(reply);
                if (ec.isError() || reply.startsWith("error ")/*fix me, why the or is needed?*/) {
                    System.out.println("failed - " + reply.replaceAll("for request 'OVERWRITE.*", ""));
                    failures.add(className);
                } else {
                    System.out.println("Uploaded.");
                    passes.add(className);
                }
            }
        }
        if (failures.isEmpty()) {
            System.out.println("All looks good");
        } else {
            System.out.println("Failed to upload: " + failures.stream().collect(Collectors.joining(", ")));
            throw new RuntimeException("Failed to upload " + failures.size() + " classes from " + (passes.size() + failures.size()) + "");
        }
        return vmInfo;
    }

    private List<String> applySubPatch(List<String> patch, SingleFilePatch startEnd, List<String> linesToPatch)
            throws PatchFailedException {
        List<String> subPatch = patch.subList(startEnd.getStart(), startEnd.getEnd() + 1);
        String header1 = patch.get(startEnd.getStart());
        String header2 = patch.get(startEnd.getStart() + 1);
        if (DiffPopup.isDevNull(header1) || DiffPopup.isDevNull(header2)) {
            //it seems that google's diff is unable to create new files, dummy workaroudn follows
            List<String> patchedLines = DiffPopup.dummyCreate(linesToPatch, subPatch, isRevert);
            return patchedLines;
        } else {
            List<String> patchedLines = DiffPopup.patch(linesToPatch, subPatch, isRevert);
            return patchedLines;
        }
    }

    private String decompileBytesByDecompilerName(String base64Bytes, String pluginName, String className, VmInfo vmInfo) throws Exception {
        return Lib.decompileBytesByDecompilerName(base64Bytes, pluginName, className, vmInfo, vmManager, pluginManager);
    }
}