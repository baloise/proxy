package com.baloise.proxy.ui;

import java.util.AbstractMap;
import java.util.Map;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.baloise.proxy.ui.ProxyUI.PasswordDialogResult;

public class PasswordDialogSwt extends Dialog {

	public Map.Entry<PasswordDialogResult, String> result = new AbstractMap.SimpleEntry<>(PasswordDialogResult.CANCEL, "");
	private Text password;
	private String passwordString;

	public PasswordDialogSwt(Shell parentShell) {
		super(parentShell);
		setShellStyle(SWT.CLOSE | SWT.PRIMARY_MODAL | SWT.BORDER | SWT.TITLE);
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		addSelectionListener(createButton(parent, IDialogConstants.OK_ID, "Set", true), PasswordDialogResult.SET,passwordString);
		addSelectionListener(createButton(parent, IDialogConstants.OK_ID, "Remove", false), PasswordDialogResult.REMOVE,"");
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
	}

	public void addSelectionListener(final Button button, PasswordDialogResult res, String pwd) {
		button.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				result = new AbstractMap.SimpleEntry<>(res, pwd);
				close();
			}
		});
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);

		Label label = new Label(container, SWT.NONE);
		label.setText("Proxy password");

		GridData data = new GridData();
		data.grabExcessHorizontalSpace = true;
		data.horizontalAlignment = GridData.FILL;

		password = new Text(container, SWT.SINGLE | SWT.BORDER | SWT.PASSWORD);
		password.addModifyListener((e) -> passwordString = password.getText());
		password.setLayoutData(data);
		return container;
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Proxy");
	}

	@Override
	protected Point getInitialSize() {
		return new Point(450, 150);
	}

}