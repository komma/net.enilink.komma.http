package net.enilink.komma.http.properties;

import net.enilink.komma.edit.ui.views.AbstractEditingDomainView;

public class HtmlPropertyEditorView extends AbstractEditingDomainView {
	public HtmlPropertyEditorView() {
		setEditPart(new HtmlPropertyEditorPart());
	}

	@Override
	protected void installSelectionProvider() {
		// do not install the selection provider
	}
}