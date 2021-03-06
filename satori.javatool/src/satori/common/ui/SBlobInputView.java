package satori.common.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.TransferHandler;

import satori.common.SInput;
import satori.data.SBlob;
import satori.main.SFrame;
import satori.task.STaskException;
import satori.task.STaskHandler;
import satori.task.STaskManager;

public class SBlobInputView implements SPaneView {
	public static interface BlobLoader {
		Map<String, SBlob> getBlobs(STaskHandler handler) throws STaskException;
	}
	
	private final SInput<SBlob> data;
	
	private JComponent pane;
	private JButton clear_button;
	private JButton label;
	private JTextField field;
	private BlobLoader blob_loader;
	private boolean edit_mode = false;
	private Font set_font, unset_font;
	private Color default_color;
	
	public SBlobInputView(SInput<SBlob> data) {
		this.data = data;
		this.blob_loader = null;
		initialize();
	}
	public SBlobInputView(SInput<SBlob> data, BlobLoader blob_loader) {
		this.data = data;
		this.blob_loader = blob_loader;
		initialize();
	}
	
	@Override public JComponent getPane() { return pane; }
	
	private static class LoadRemoteDialog {
		private final BlobLoader blob_loader;
		private JDialog dialog;
		private JList list;
		private boolean confirmed = false;
		
		public LoadRemoteDialog(BlobLoader blob_loader) {
			this.blob_loader = blob_loader;
			initialize();
		}
		
		private void initialize() {
			dialog = new JDialog(SFrame.get().getFrame(), "Load remote", true);
			dialog.getContentPane().setLayout(new BorderLayout());
			list = new JList();
			list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			list.addMouseListener(new MouseAdapter() {
				@Override public void mouseClicked(MouseEvent e) {
					if (e.getClickCount() != 2) return;
					e.consume();
					confirmed = true;
					dialog.setVisible(false);
				}
			});
			JScrollPane list_pane = new JScrollPane(list);
			list_pane.setPreferredSize(new Dimension(200, 100));
			dialog.getContentPane().add(list_pane, BorderLayout.CENTER);			
			JPanel button_pane = new JPanel(new FlowLayout(FlowLayout.CENTER));
			JButton confirm = new JButton("OK");
			confirm.addActionListener(new ActionListener() {
				@Override public void actionPerformed(ActionEvent e) {
					confirmed = true;
					dialog.setVisible(false);
				}
			});
			button_pane.add(confirm);
			JButton cancel = new JButton("Cancel");
			cancel.addActionListener(new ActionListener() {
				@Override public void actionPerformed(ActionEvent e) {
					dialog.setVisible(false);
				}
			});
			button_pane.add(cancel);
			dialog.getContentPane().add(button_pane, BorderLayout.SOUTH);
			dialog.pack();
			dialog.setLocationRelativeTo(SFrame.get().getFrame());
		}
		
		public SBlob process(STaskHandler handler) throws STaskException {
			Map<String, SBlob> blobs = blob_loader.getBlobs(handler);
			Vector<String> names = new Vector<String>(blobs.keySet());
			Collections.sort(names);
			list.setListData(names);
			dialog.setVisible(true);
			if (!confirmed) return null;
			int index = list.getSelectedIndex();
			if (index == -1) return null;
			return blobs.get(names.get(index));
		}
	}
	
	private void loadRemote() {
		if (blob_loader == null) return;
		LoadRemoteDialog dialog = new LoadRemoteDialog(blob_loader);
		STaskHandler handler = STaskManager.getHandler();
		try {
			SBlob blob = dialog.process(handler);
			if (blob == null) return;
			data.set(handler, blob);
		}
		catch(STaskException ex) {}
		finally { handler.close(); }
	}
	private void loadFile() {
		JFileChooser file_chooser = new JFileChooser();
		file_chooser.setSelectedFile(data.get() != null ? data.get().getFile() : null);
		int ret = file_chooser.showDialog(SFrame.get().getFrame(), "Load");
		if (ret != JFileChooser.APPROVE_OPTION) return;
		STaskHandler handler = STaskManager.getHandler();
		try { data.set(handler, SBlob.createLocal(handler, file_chooser.getSelectedFile())); }
		catch(STaskException ex) {}
		finally { handler.close(); }
	}
	private void editFile() {
		SBlob result = new SEditDialog().process(data.get());
		if (result == null) return;
		STaskHandler handler = STaskManager.getHandler();
		try { data.set(handler, result); }
		catch(STaskException ex) {}
		finally { handler.close(); }
	}
	private void saveFile() {
		if (data.get() == null) return;
		JFileChooser file_chooser = new JFileChooser();
		String name = data.get().getName();
		if (name != null && !name.isEmpty()) file_chooser.setSelectedFile(new File(file_chooser.getCurrentDirectory(), name));
		int ret = file_chooser.showDialog(SFrame.get().getFrame(), "Save");
		if (ret != JFileChooser.APPROVE_OPTION) return;
		STaskHandler handler = STaskManager.getHandler();
		try { data.get().saveLocal(handler, file_chooser.getSelectedFile()); }
		catch(STaskException ex) {}
		finally { handler.close(); }
	}
	private void rename() {
		if (edit_mode || data.get() == null) return;
		edit_mode = true;
		field.setText(data.get().getName());
		field.selectAll();
		field.setVisible(true); 
		field.requestFocus();
		label.setVisible(false);
	}
	private void renameDone(boolean focus) {
		if (!edit_mode) return;
		SBlob new_data = data.get().rename(field.getText());
		STaskHandler handler = STaskManager.getHandler();
		try { data.set(handler, new_data); }
		catch(STaskException ex) { return; }
		finally { handler.close(); }
		edit_mode = false;
		label.setVisible(true);
		if (focus) label.requestFocus();
		field.setVisible(false);
	}
	private void renameCancel() {
		if (!edit_mode) return;
		edit_mode = false;
		label.setVisible(true);
		label.requestFocus();
		field.setVisible(false);
	}
	private void clear() {
		STaskHandler handler = STaskManager.getHandler();
		try { data.set(handler, null); }
		catch(STaskException ex) {}
		finally { handler.close(); }
	}
	
	private void showPopup(Point location) {
		JPopupMenu popup = new JPopupMenu();
		if (blob_loader != null) {
			JMenuItem loadRemoteItem = new JMenuItem("Load remote");
			loadRemoteItem.addActionListener(new ActionListener() {
				@Override public void actionPerformed(ActionEvent e) { loadRemote(); }
			});
			popup.add(loadRemoteItem);
		}
		JMenuItem loadItem = new JMenuItem("Load file");
		loadItem.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent e) { loadFile(); }
		});
		popup.add(loadItem);
		JMenuItem editItem = new JMenuItem("Edit");
		editItem.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent e) { editFile(); }
		});
		popup.add(editItem);
		JMenuItem renameItem = new JMenuItem("Rename");
		renameItem.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent e) { rename(); }
		});
		popup.add(renameItem);
		JMenuItem saveItem = new JMenuItem("Save");
		saveItem.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent e) { saveFile(); }
		});
		popup.add(saveItem);
		if (location != null) popup.show(label, location.x, location.y);
		else popup.show(label, 0, label.getHeight());
	}
	
	private static DataFlavor sFileFlavor = new DataFlavor(SBlob.class, "Satori file");
	private static DataFlavor stdFileListFlavor = DataFlavor.javaFileListFlavor;
	private static DataFlavor nixFileListFlavor = new DataFlavor("text/uri-list;class=java.lang.String", "Unix file list");
	
	private static class SFileTransferable implements Transferable {
		private final SBlob data;
		
		public SFileTransferable(SBlob data) { this.data = data; }
		
		@Override public DataFlavor[] getTransferDataFlavors() {
			DataFlavor[] flavors = new DataFlavor[1];
			flavors[0] = sFileFlavor;
			return flavors;
		}
		@Override public boolean isDataFlavorSupported(DataFlavor flavor) {
			return flavor.match(sFileFlavor);
		}
		@Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
			if (flavor.match(sFileFlavor)) return data;
			else throw new UnsupportedFlavorException(flavor);
		}
	}
	
	@SuppressWarnings("unchecked")
	private static List<File> importStdFileList(Object obj) {
		try { return (List<File>)obj; }
		catch(Exception ex) { return null; }
	}
	private static List<File> importNixFileList(Object obj) {
		String data;
		try { data = (String)obj; }
		catch(Exception ex) { return null; }
		List<File> list = new ArrayList<File>();
		for (StringTokenizer st = new StringTokenizer(data, "\r\n"); st.hasMoreTokens();) {
			String token = st.nextToken().trim();
			if (token.isEmpty() || token.startsWith("#")) continue;
			File file;
			try { file = new File(new URI(token)); }
			catch(Exception ex) { return null; }
			list.add(file);
		}
		return list;
	}
	
	@SuppressWarnings("serial")
	private class SFileTransferHandler extends TransferHandler {
		@Override public boolean canImport(TransferSupport support) {
			if ((support.getSourceDropActions() & COPY) != COPY) return false;
			support.setDropAction(COPY);
			if (support.isDataFlavorSupported(sFileFlavor)) return true;
			if (support.isDataFlavorSupported(stdFileListFlavor)) return true;
			if (support.isDataFlavorSupported(nixFileListFlavor)) return true;
			return false;
		}
		@Override public boolean importData(TransferSupport support) {
			if (!support.isDrop()) return false;
			Transferable t = support.getTransferable();
			if (support.isDataFlavorSupported(sFileFlavor)) {
				SBlob object;
				try { object = (SBlob)t.getTransferData(sFileFlavor); }
				catch(Exception ex) { return false; }
				STaskHandler handler = STaskManager.getHandler();
				try { data.set(handler, object); }
				catch(STaskException ex) { return false; }
				finally { handler.close(); }
				return true;
			}
			List<File> file_list = null;
			try {
				if (support.isDataFlavorSupported(stdFileListFlavor))
					file_list = importStdFileList(t.getTransferData(stdFileListFlavor));
				else if (support.isDataFlavorSupported(nixFileListFlavor))
					file_list = importNixFileList(t.getTransferData(nixFileListFlavor));
			}
			catch(Exception ex) { return false; }
			if (file_list == null || file_list.size() != 1) return false;
			STaskHandler handler = STaskManager.getHandler();
			try { data.set(handler, SBlob.createLocal(handler, file_list.get(0))); }
			catch(STaskException ex) { return false; }
			finally { handler.close(); }
			return true;
		}
		@Override protected Transferable createTransferable(JComponent c) { return new SFileTransferable(data.get()); }
		@Override public int getSourceActions(JComponent c) { return COPY; }
		@Override protected void exportDone(JComponent source, Transferable data, int action) {}
	}
	
	private void initialize() {
		pane = new JPanel(new SLayoutManagerAdapter() {
			@Override public void layoutContainer(Container parent) {
				Dimension dim = parent.getSize();
				clear_button.setBounds(0, (dim.height-13)/2, 13, 13);
				label.setBounds(15, 0, dim.width-15, dim.height);
				field.setBounds(15, 0, dim.width-15, dim.height);
			}
		});
		byte[] icon = {71,73,70,56,57,97,7,0,7,0,-128,1,0,-1,0,0,-1,-1,-1,33,-7,4,1,10,0,1,0,44,0,0,0,0,7,0,7,0,0,2,13,12,126,6,-63,-72,-36,30,76,80,-51,-27,86,1,0,59};
		clear_button = new JButton(new ImageIcon(icon));
		clear_button.setMargin(new Insets(0, 0, 0, 0));
		clear_button.setToolTipText("Clear");
		clear_button.setFocusable(false);
		clear_button.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent e) { clear(); }
		});
		pane.add(clear_button);
		label = new JButton();
		label.setBorder(BorderFactory.createEmptyBorder(0, 1, 0, 1));
		label.setBorderPainted(false);
		label.setContentAreaFilled(false);
		label.setOpaque(false);
		label.setHorizontalAlignment(SwingConstants.LEADING);
		label.setToolTipText(data.getDescription());
		MouseAdapter label_listener = new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) { showPopup(e.getPoint()); }
			@Override public void mouseDragged(MouseEvent e) {
				label.getTransferHandler().exportAsDrag(label, e, TransferHandler.COPY);
			}
		};
		label.addMouseListener(label_listener);
		label.addMouseMotionListener(label_listener);
		label.addKeyListener(new KeyAdapter() {
			@Override public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) { e.consume(); showPopup(null); }
			}
		});
		label.setTransferHandler(new SFileTransferHandler());
		pane.add(label);
		field = new JTextField();
		field.setVisible(false);
		field.addKeyListener(new KeyAdapter() {
			@Override public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) { e.consume(); renameDone(true); }
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE) { e.consume(); renameCancel(); }
			}
		});
		field.addFocusListener(new FocusAdapter() {
			@Override public void focusLost(FocusEvent e) { renameDone(false); }
		});
		pane.add(field);
		set_font = label.getFont().deriveFont(Font.PLAIN);
		unset_font = label.getFont().deriveFont(Font.ITALIC);
		default_color = pane.getBackground();
		update();
	}
	
	@Override public void update() {
		pane.setBackground(data.isValid() ? default_color : Color.YELLOW);
		label.setFont(data.getText() != null ? set_font : unset_font);
		label.setText(data.getText() != null ? data.getText() : data.getDescription() != null ? data.getDescription() : "Not set");
	}
}
