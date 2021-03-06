package satori.common.ui;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.TransferHandler;

import satori.common.SInput;
import satori.task.STaskException;
import satori.task.STaskHandler;
import satori.task.STaskManager;

public class SStringInputView implements SPaneView {
	private final SInput<String> data;
	
	private JComponent pane;
	private JButton clear_button;
	private JButton label;
	private JTextField field;
	private boolean edit_mode = false;
	private Font set_font, unset_font;
	private Color default_color;
	
	public SStringInputView(SInput<String> data) {
		this.data = data;
		initialize();
	}
	
	@Override public JComponent getPane() { return pane; }
	
	private void edit() {
		if (edit_mode) return;
		edit_mode = true;
		field.setText(data.get());
		field.selectAll();
		field.setVisible(true); 
		field.requestFocus();
		label.setVisible(false);
	}
	private void editDone(boolean focus) {
		if (!edit_mode) return;
		String new_data = field.getText().isEmpty() ? null : field.getText();
		STaskHandler handler = STaskManager.getHandler();
		try { data.set(handler, new_data); }
		catch(STaskException ex) { return; }
		finally { handler.close(); }
		edit_mode = false;
		label.setVisible(true);
		if (focus) label.requestFocus();
		field.setVisible(false);
	}
	private void editCancel() {
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
	
	@SuppressWarnings("serial")
	private class LabelTransferHandler extends TransferHandler {
		@Override public boolean canImport(TransferSupport support) {
			if (!support.isDataFlavorSupported(DataFlavor.stringFlavor)) return false;
			if ((support.getSourceDropActions() & COPY) == COPY) {
				support.setDropAction(COPY);
				return true;
			}
			return false;
		}
		@Override public boolean importData(TransferSupport support) {
			if (!support.isDrop()) return false;
			Transferable t = support.getTransferable();
			String object;
			try { object = (String)t.getTransferData(DataFlavor.stringFlavor); }
			catch (Exception e) { return false; }
			if (object != null && object.isEmpty()) object = null;
			STaskHandler handler = STaskManager.getHandler();
			try { data.set(handler, object); }
			catch(STaskException ex) { return false; }
			finally { handler.close(); }
			return true;
		}
		@Override protected Transferable createTransferable(JComponent c) { return new StringSelection(data.get()); }
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
			@Override public void mouseClicked(MouseEvent e) { edit(); }
			@Override public void mouseDragged(MouseEvent e) {
				label.getTransferHandler().exportAsDrag(label, e, TransferHandler.COPY);
			}
		};
		label.addMouseListener(label_listener);
		label.addMouseMotionListener(label_listener);
		label.addKeyListener(new KeyAdapter() {
			@Override public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) { e.consume(); edit(); }
			}
		});
		label.setTransferHandler(new LabelTransferHandler());
		pane.add(label);
		field = new JTextField();
		field.setVisible(false);
		field.addKeyListener(new KeyAdapter() {
			@Override public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) { e.consume(); editDone(true); }
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE) { e.consume(); editCancel(); }
			}
		});
		field.addFocusListener(new FocusAdapter() {
			@Override public void focusLost(FocusEvent e) { editDone(false); }
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
