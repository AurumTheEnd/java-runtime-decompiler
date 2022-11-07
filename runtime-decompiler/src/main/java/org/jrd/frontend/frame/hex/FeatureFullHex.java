package org.jrd.frontend.frame.hex;

import org.jrd.frontend.frame.main.decompilerview.HexWithControls;
import org.jrd.frontend.frame.main.decompilerview.LinesProvider;
import org.jrd.frontend.frame.main.popup.DiffPopup;
import org.jrd.frontend.utility.ImageButtonFactory;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

public class FeatureFullHex extends JPanel {

    private final HexWithControls hex;

    public FeatureFullHex(final File f, final JTabbedPane parent) throws IOException {
        this.setName(f.getName());
        this.setLayout(new BorderLayout());
        JPanel tool = new JPanel();
        tool.setLayout(new GridLayout(1, 6));
        JButton undo = ImageButtonFactory.createUndoButton();
        tool.add(undo);
        JButton redo = ImageButtonFactory.createRedoButton();
        tool.add(redo);
        JButton diff = new JButton("Diff");
        diff.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                new DiffPopup(toLines(parent), Optional.empty(), true).show(diff, 0, 0);
            }

            private List<LinesProvider> toLines(JTabbedPane parent) {
                //selcted have to be first, so the  onlyOne can do its job proeprly
                int selected = parent.getSelectedIndex();
                Component[] comps = parent.getComponents();
                List<LinesProvider> r = new ArrayList<>();
                for (int i = selected; i < comps.length - 1/*the plus button*/ ; i++) {
                    HexWithControls featureFullHex = (HexWithControls) (((JPanel) comps[i]).getComponents()[1]);
                    r.add(featureFullHex);
                }
                for (int i = 0; i < selected; i++) {
                    HexWithControls featureFullHex = (HexWithControls) (((JPanel) comps[i]).getComponents()[1]);
                    r.add(featureFullHex);
                }
                return r;
            }
        });
        tool.add(diff);
        JButton save = (new JButton("Save"));
        JButton open = (new JButton("Open"));
        JButton close = (new JButton("Close"));
        tool.add(save);
        tool.add(open);
        tool.add(close);
        this.add(tool, BorderLayout.NORTH);
        hex = new HexWithControls(null);
        hex.setFile(f);
        hex.open(Files.readAllBytes(f.toPath()));
        this.add(hex, BorderLayout.CENTER);
        save.addActionListener(a -> {
            JFileChooser jFileChooser = new JFileChooser(hex.getFile());
            int fo = jFileChooser.showSaveDialog(hex);
            File nwf = jFileChooser.getSelectedFile();
            if (fo == JFileChooser.APPROVE_OPTION && nwf != null) {
                try {
                    Files.write(nwf.toPath(), hex.get());
                    hex.setFile(nwf);
                    FeatureFullHex.this.setName(nwf.getName());
                    updateTitles(parent);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(hex, ex.getMessage());
                }
            }
        });
        open.addActionListener(a -> {
            JFileChooser jFileChooser = new JFileChooser(hex.getFile());
            int fo = jFileChooser.showOpenDialog(hex);
            File nwf = jFileChooser.getSelectedFile();
            if (fo == JFileChooser.APPROVE_OPTION && nwf != null) {
                try {
                    byte[] nwBytes = Files.readAllBytes(nwf.toPath());
                    hex.setFile(nwf);
                    hex.open(nwBytes);
                    FeatureFullHex.this.setName(nwf.getName());
                    updateTitles(parent);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(hex, ex.getMessage());
                }
            }
        });
        close.addActionListener(a -> {
            parent.remove(FeatureFullHex.this);
        });
        undo.addActionListener(a->{
            hex.undo();
        });
        redo.addActionListener(a->{
            hex.redo();
        });
    }

    private static void updateTitles(JTabbedPane parent) {
        for (int x = 0; x < parent.getComponentCount(); x++) {
            parent.setTitleAt(x, parent.getComponent(x).getName());
        }
    }
}