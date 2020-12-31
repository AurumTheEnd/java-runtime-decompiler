package org.jrd.frontend.MainFrame;

import org.jc.api.ClassIdentifier;
import org.jc.api.ClassesProvider;
import org.jc.api.IdentifiedBytecode;
import org.jc.api.IdentifiedSource;
import org.jc.api.InMemoryCompiler;
import org.jc.api.MessagesListener;
import org.jrd.backend.communication.RuntimeCompilerConnector;
import org.jrd.backend.core.OutputController;
import org.jrd.backend.data.VmInfo;
import org.jrd.backend.data.VmManager;
import org.jrd.backend.decompiling.DecompilerWrapperInformation;
import org.jrd.backend.decompiling.PluginManager;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Optional;
import java.util.logging.Level;

public class RewriteClassDialog extends JDialog {

    private static final String[] saveOptions = new String[]{"fully qualified name", "src subdirectories name", "custom name"};
    private static int FULLY_QUALIFIED_NAME = 0;
    private static int SRC_SUBDIRS_NAME = 1;
    private static int CUSTOM_NAME = 2;
    private final JTabbedPane dualpane;

    private final JPanel currentBufferPane;
    private final JLabel currentClass;
    private final JButton selectSrcTarget;
    private final JTextField futureSrcTarget;
    private final JComboBox<String> namingSource;
    private final JButton selectBinTarget;
    private final JTextField futureBinTarget;
    private final JComboBox<String> namingBinary;
    private final JButton saveSrcBuffer;
    private final JButton compileAndSave;
    private final JButton compileAndUpload;
    private final JTextField statusCompileCurrentBuffer;

    private final JPanel manualPane;
    private final JPanel inputs;
    private final JPanel buttons;
    private final JLabel validation;
    private final JTextField filePath;
    private final JTextField className;
    private final JButton selectSrc;
    private final JLabel nothing;
    private final JButton ok;
    private boolean wasOkPressed;

    private final JPanel externalFiles;
    private final JTextField filesToCompile;
    private final JButton selectExternalFiles;
    private final JCheckBox recursive;
    private final JTextField outputExternalFilesDir;
    private final JComboBox<String> namingExternal;
    private final JButton selectExternalFilesSave;
    private final JButton compileExternalFiles;
    private final JButton compileExternalFilesAndUpload;
    private final JTextField statusExternalFiles;


    private final String origName;
    private final String origBuffer;
    private final VmInfo vmInfo;
    private final VmManager vmManager;

    public RewriteClassDialog(final String name, final String lastLoad, final String currentBuffer, final String lastSaveSrc, final String lastSaveBin, VmInfo vmInfo, VmManager vmManager, PluginManager pluginManager, DecompilerWrapperInformation selectedDecompiler) {
        super((JFrame) null, "Specify class and selectSrc its bytecode", true);
        this.setSize(400, 400);
        this.setLayout(new BorderLayout());

        this.origName = name;
        this.origBuffer = currentBuffer;
        this.vmInfo = vmInfo;
        this.vmManager = vmManager;

        dualpane = new JTabbedPane();

        currentBufferPane = new JPanel();
        currentBufferPane.setName("Current buffer");
        currentBufferPane.setLayout(new GridLayout(0, 1));
        statusCompileCurrentBuffer = new JTextField();
        statusCompileCurrentBuffer.setEditable(false);
        if (origBuffer == null || origBuffer.length() == 0) {
            currentClass = new JLabel(origName + " !!MISSING!!");
        } else {
            currentClass = new JLabel(origName + " - " + origBuffer.length() + " chars");
        }
        saveSrcBuffer = new JButton("Save current buffer");
        compileAndSave = new JButton("Compile and save as");
        namingBinary = new JComboBox<String>(saveOptions);
        namingSource = new JComboBox<String>(saveOptions);
        namingSource.setSelectedIndex(FULLY_QUALIFIED_NAME);
        namingBinary.setSelectedIndex(SRC_SUBDIRS_NAME);
        futureBinTarget = new JTextField(lastSaveBin);
        futureSrcTarget = new JTextField(lastSaveSrc);
        selectBinTarget = new JButton("...");
        selectSrcTarget = new JButton("...");
        compileAndUpload = new JButton("Compile and directly upload");
        compileAndUpload.setFont(compileAndSave.getFont().deriveFont(Font.BOLD));

        manualPane = new JPanel();
        manualPane.setName("Manual uplaod from file");
        manualPane.setLayout(new BorderLayout());
        inputs = new JPanel(new GridLayout(3, 1));
        buttons = new JPanel(new GridLayout(3, 1));
        validation = new JLabel("???");
        filePath = new JTextField(lastLoad);
        className = new JTextField(name);
        selectSrc = new JButton("...");
        nothing = new JLabel();
        ok = new JButton("ok");
        wasOkPressed = false;

        externalFiles = new JPanel(new GridLayout(0, 1));
        externalFiles.setName("Compile external files");
        externalFiles.add(new JLabel("Select external files to compile against runtime classpath"), BorderLayout.NORTH);
        JPanel exFilesIn = new JPanel(new BorderLayout());
        filesToCompile = new JTextField("todo");
        exFilesIn.add(filesToCompile, BorderLayout.CENTER);
        recursive = new JCheckBox("recursive");
        exFilesIn.add(recursive, BorderLayout.WEST);
        selectExternalFiles = new JButton("...");
        exFilesIn.add(selectExternalFiles, BorderLayout.EAST);
        externalFiles.add(exFilesIn);
        outputExternalFilesDir = new JTextField("todo");
        namingExternal = new JComboBox<>(saveOptions);
        namingExternal.setSelectedIndex(SRC_SUBDIRS_NAME);
        selectExternalFilesSave = new JButton("...");
        JPanel saveExFilesIn = new JPanel(new BorderLayout());
        saveExFilesIn.add(selectExternalFilesSave, BorderLayout.EAST);
        saveExFilesIn.add(outputExternalFilesDir, BorderLayout.CENTER);
        saveExFilesIn.add(namingExternal, BorderLayout.WEST);
        externalFiles.add(saveExFilesIn);
        compileExternalFiles = new JButton("Compile and save");
        externalFiles.add(compileExternalFiles);
        compileExternalFilesAndUpload = new JButton("Compile and upload");
        compileExternalFilesAndUpload.setFont(compileExternalFilesAndUpload.getFont().deriveFont(Font.BOLD));
        externalFiles.add(compileExternalFilesAndUpload);
        statusExternalFiles = new JTextField("");
        statusExternalFiles.setEditable(false);
        externalFiles.add(statusExternalFiles);

        setLocation(ScreenFinder.getCurrentPoint());
        setValidation();
        setSelectListener();
        setOkListener();
        adds();

        try {
            boolean haveDecompiler = pluginManager.haveCompiler(selectedDecompiler);
            String s = "Default runtime compiler will be used";
            if (haveDecompiler) {
                s = selectedDecompiler.getName() + " plugin is delivered with its own compiler!!";
            }
            statusExternalFiles.setText(s);
            statusCompileCurrentBuffer.setText(s);
        } catch (Exception ex) {
            statusExternalFiles.setText(ex.getMessage());
            statusCompileCurrentBuffer.setText(ex.getMessage());
            dualpane.setSelectedIndex(1);
        }


        saveSrcBuffer.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                saveByGui(futureSrcTarget.getText(), namingSource.getSelectedIndex(), ".java", statusCompileCurrentBuffer, origName, origBuffer.getBytes());

            }
        });
    }


    private static boolean saveByGui(String fileNameBase, int naming, String suffix, JTextField status, String clazz, byte[] content) {
        String name = "???";
        String ss = "Error to save: ";
        boolean r = true;
        try {
            name = cheatName(fileNameBase, naming, suffix, clazz);
            File f = new File(name);
            if (naming == SRC_SUBDIRS_NAME) {
                f.getParentFile().mkdirs();
            }
            Files.write(f.toPath(), content);
            ss = "Saved: ";
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, ex.getMessage());
            r = false;
        }
        status.setText(ss + name);
        return r;
    }

    private static String cheatName(String base, int selectedIndex, String suffix, String fullyClasifiedName) {
        if (selectedIndex == CUSTOM_NAME) {
            return base;
        }
        if (selectedIndex == FULLY_QUALIFIED_NAME) {
            return base + "/" + fullyClasifiedName + suffix;
        }
        if (selectedIndex == SRC_SUBDIRS_NAME) {
            return base + "/" + fullyClasifiedName.replaceAll("\\.", "/") + suffix;
        }
        throw new RuntimeException("Unknown name target " + selectedIndex);
    }

    private void setValidation() {
        DocumentListener v = new FiletoClassValidator(validation, filePath, className);
        filePath.getDocument().addDocumentListener(v);
        className.getDocument().addDocumentListener(v);
        v.changedUpdate(null);
    }

    private void setSelectListener() {
        selectSrc.addActionListener(e -> {
            JFileChooser jf = new JFileChooser(filePath.getText());
            jf.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int returnVal = jf.showOpenDialog(selectSrc);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                filePath.setText(jf.getSelectedFile().getAbsolutePath());
            }
        });
    }


    private static void setSelectSaveListenr(JButton selectTarget, JTextField futureTarget, JComboBox<String> naming) {
        selectTarget.addActionListener(e -> {
            JFileChooser jf = new JFileChooser(futureTarget.getText());
            if (naming.getSelectedIndex() < CUSTOM_NAME) {
                jf.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            } else {
                jf.setFileSelectionMode(JFileChooser.FILES_ONLY);
            }
            int returnVal = jf.showOpenDialog(selectTarget);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                futureTarget.setText(jf.getSelectedFile().getAbsolutePath());
            }
        });
    }

    private void setOkListener() {
        setSelectSaveListenr(selectSrcTarget, futureSrcTarget, namingSource);
        setSelectSaveListenr(selectBinTarget, futureBinTarget, namingBinary);
        setSelectSaveListenr(selectExternalFilesSave, outputExternalFilesDir, namingExternal);
        selectExternalFiles.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JFileChooser jf = new JFileChooser(filesToCompile.getText());
                jf.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                int returnVal = jf.showOpenDialog(selectExternalFiles);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    filesToCompile.setText(jf.getSelectedFile().getAbsolutePath());
                }
            }
        });
        ok.addActionListener(e -> {
            this.wasOkPressed = true;
            this.setVisible(false);
        });

        compileAndSave.addActionListener(actionEvent -> {
            IdentifiedSource currrentIs = new IdentifiedSource(new ClassIdentifier(origName), origBuffer.getBytes(), Optional.empty());
            new SavingCompilerOutputAction(statusCompileCurrentBuffer, vmInfo, vmManager, namingBinary.getSelectedIndex(), futureBinTarget.getText()).run(currrentIs);
        });

        compileExternalFiles.addActionListener(actionEvent -> {
            String[] srcs = filesToCompile.getText().split("\\s+");
            IdentifiedSource[] loaded = new IdentifiedSource[srcs.length];
            try {
                for (int i = 0; i < srcs.length; i++) {
                    loaded[i] = new IdentifiedSource(new ClassIdentifier(guessClass(srcs[i])), Files.readAllBytes(new File(srcs[i]).toPath()), Optional.empty());
                }
                new SavingCompilerOutputAction(statusExternalFiles, vmInfo, vmManager, namingExternal.getSelectedIndex(), outputExternalFilesDir.getText()).run(loaded);
            } catch (Exception ex) {
                ex.printStackTrace();
                statusExternalFiles.setText(ex.getMessage());
                JOptionPane.showMessageDialog(null, ex.getMessage());
            }
        });
    }

    private String guessClass(String src) {
        //todo, parse package and classname from .java file.
        //then use it. if it fails, warnn user severely and advise him to terminate action
        JOptionPane.showMessageDialog(null, "Not implemented, hoe it dies");
        return src;
    }


    private static CompilationWithResult xompileWithGui(VmInfo vmInfo, VmManager vmManager, IdentifiedSource... sources) {
        ClassesProvider cp = new RuntimeCompilerConnector.JRDClassesProvider(vmInfo, vmManager);
        InMemoryCompiler rc = new RuntimeCompilerConnector.DummyRuntimeCompiler();
        JDialog compialtionRunningDialog = new JDialog((JFrame) null, "Compiling", true);
        JTextArea compilationLog = new JTextArea();
        compialtionRunningDialog.setSize(300, 400);
        compialtionRunningDialog.add(new JScrollPane(compilationLog));
        CompilationWithResult compiler = new CompilationWithResult(rc, cp, compilationLog, sources);
        Thread t = new Thread(compiler);
        t.start();
        compialtionRunningDialog.setLocationRelativeTo(null);
        compialtionRunningDialog.setVisible(true);
        return compiler;
    }

    private void adds() {
        inputs.add(filePath);
        inputs.add(className);
        inputs.add(className);
        inputs.add(validation);
        buttons.add(selectSrc);
        buttons.add(nothing);
        buttons.add(ok);
        manualPane.add(inputs);
        manualPane.add(buttons, BorderLayout.EAST);

        currentBufferPane.add(currentClass);
        JPanel p11 = new JPanel(new BorderLayout());
        JPanel p12 = new JPanel(new GridLayout(1, 2));
        p11.add(selectSrcTarget, BorderLayout.WEST);
        p11.add(p12);
        p12.add(futureSrcTarget);
        p12.add(namingSource);
        currentBufferPane.add(p11);
        currentBufferPane.add(saveSrcBuffer);
        JPanel p21 = new JPanel(new BorderLayout());
        JPanel p22 = new JPanel(new GridLayout(1, 2));
        p21.add(selectBinTarget, BorderLayout.WEST);
        p21.add(p22);
        p22.add(futureBinTarget);
        p22.add(namingBinary);
        currentBufferPane.add(p21);
        currentBufferPane.add(compileAndSave);
        currentBufferPane.add(compileAndUpload);
        currentBufferPane.add(statusCompileCurrentBuffer);
        dualpane.add(currentBufferPane);
        dualpane.add(manualPane);
        dualpane.add(externalFiles);
        this.add(dualpane);
        this.pack();
    }

    public boolean isOkPressed() {
        return this.wasOkPressed;
    }

    public String getClassName() {
        return this.className.getText();
    }

    public String getLoadFilePath() {
        return this.filePath.getText();
    }

    public String getSaveSrcPath() {
        return this.futureSrcTarget.getText();
    }

    public String getSaveBinPath() {
        return this.futureBinTarget.getText();
    }

    private static class CompilationWithResult implements Runnable {
        private final InMemoryCompiler rc;
        private final ClassesProvider cp;
        private final JTextArea compilationLog;
        private final IdentifiedSource[] sources;
        private Exception ex;
        private Collection<IdentifiedBytecode> result;


        public CompilationWithResult(InMemoryCompiler rc, ClassesProvider cp, JTextArea compilationLog, IdentifiedSource... sources) {
            this.rc = rc;
            this.cp = cp;
            this.compilationLog = compilationLog;
            this.sources = sources;
        }

        @Override
        public void run() {
            try {
                result = rc.compileClass(cp, Optional.of(new MessagesListener() {
                    @Override
                    public void addMessage(Level level, String s) {
                        OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL, s);
                        compilationLog.setText(compilationLog.getText() + s + "\n");
                    }
                }), sources);
            } catch (Exception ex) {
                this.ex = ex;
                ex.printStackTrace();
                compilationLog.setText(compilationLog.getText() + ex.getMessage() + "\n");
            } finally {
                OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL, "Operation finished");
                compilationLog.setText(compilationLog.getText() + "Operatin finished, you may close dialog\n");
            }

        }
    }

    private static class SavingCompilerOutputAction {

        private final JTextField status;
        private final VmInfo vmInfo;
        private final VmManager vmManager;
        private final int namingSchema;
        private final String destination;

        public SavingCompilerOutputAction(JTextField status, VmInfo vmInfo, VmManager vmManager, int namingSchema, String destination) {
            this.status = status;
            this.vmInfo = vmInfo;
            this.vmManager = vmManager;
            this.namingSchema = namingSchema;
            this.destination = destination;
        }

        public void run(IdentifiedSource... sources) {
            RewriteClassDialog.CompilationWithResult compiler = xompileWithGui(this.vmInfo, this.vmManager, sources);
            if (compiler.ex == null && compiler.result == null) {
                String s = "No output from compiler, maybe still running?";
                JOptionPane.showMessageDialog(null, s);
                status.setText(s);
            } else if (compiler.ex != null) {
                JOptionPane.showMessageDialog(null, compiler.ex.getMessage());
                status.setText("Failed - " + compiler.ex.getMessage());
            } else if (compiler.result != null) {
                status.setText("something done, will save now");
                status.repaint();
                if (namingSchema == CUSTOM_NAME) {
                    if (compiler.result.size() != 1) {
                        String s = "Output of compilation was " + compiler.result.size() + "classes. Can not save more then one file to exact filename";
                        JOptionPane.showMessageDialog(null, s);
                        status.setText(s);
                        return;
                    }
                }
                int saved = 0;
                for (IdentifiedBytecode clazz : compiler.result) {
                    boolean r = saveByGui(destination, namingSchema, ".class", status, clazz.getClassIdentifier().getFullName(), clazz.getFile());
                    if (r) {
                        saved++;
                    }
                }
                if (compiler.result.size() > 1) {
                    if (saved == compiler.result.size()) {
                        status.setText("Saved all " + saved + "classes to" + destination);
                    } else {
                        status.setText("Saved only " + saved + " from total of " + compiler.result.size() + " classes to" + destination);
                    }
                }
            } else {
                status.setText("Really weird state, report bug how to achieve this");
            }
        }
    }
}
