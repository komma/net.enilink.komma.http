/*******************************************************************************
 * Copyright (c) 2009 Fraunhofer IWU and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Fraunhofer IWU - initial API and implementation
 *******************************************************************************/
package net.enilink.komma.http.properties;

import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.json.JSONException;
import org.json.JSONWriter;

import net.enilink.commons.iterator.IExtendedIterator;
import net.enilink.komma.common.adapter.IAdapterFactory;
import net.enilink.komma.concepts.IProperty;
import net.enilink.komma.concepts.IResource;
import net.enilink.komma.edit.provider.IItemLabelProvider;
import net.enilink.komma.edit.ui.views.AbstractEditingDomainPart;
import net.enilink.komma.http.KommaHttpPlugin;
import net.enilink.komma.model.IModel;
import net.enilink.komma.model.IObject;
import net.enilink.komma.core.ILiteral;
import net.enilink.komma.core.IReference;
import net.enilink.komma.core.IStatement;
import net.enilink.komma.core.URIImpl;
import net.enilink.komma.util.ISparqlConstants;

public class HtmlPropertyEditorPart extends AbstractEditingDomainPart {
	private IAdapterFactory adapterFactory;
	private IObject resource;

	private Browser browser;
	private boolean browserReady = false;

	private boolean includeInferred = true;

	@Override
	public void createContents(Composite parent) {
		parent.setLayout(new FillLayout());
		browser = new Browser(parent, SWT.MOZILLA);

		// create browser functions
		new BrowserFunction(browser, "loadData") {
			public Object function(Object[] arguments) {
				IObject subject = resource;
				IProperty property = null;
				if (arguments.length > 0 && resource != null) {
					subject = resource.getModel().resolve(
							URIImpl.createURI((String) arguments[0]));

					if (arguments.length > 1) {
						property = (IProperty) resource.getModel().resolve(
								URIImpl.createURI((String) arguments[1]));
					}
				}

				if (subject == null) {
					return null;
				}

				try {
					StringWriter stringWriter = new StringWriter();
					JSONWriter json = new JSONWriter(stringWriter);
					json.object();

					Set<Object> allValues = new HashSet<Object>();

					if (property == null) {
						loadProperties(json, allValues, subject);
					} else {
						loadPropertyValues(json, allValues, subject, property);
					}

					addPresentation(json, allValues);
					json.endObject();

					return stringWriter.toString();
				} catch (Exception e) {
					return null;
				}
			}
		};

		String url = KommaHttpPlugin.createHttpUrl(KommaHttpPlugin.getDefault()
				.getBundle().getEntry("resources/html/properties.html")
				.toString());
		browser.setUrl(url);
		browser.addProgressListener(new ProgressAdapter() {
			@Override
			public void completed(ProgressEvent event) {
				browserReady = true;
				if (resource != null) {
					refreshBrowser();
				}
			}
		});
	}

	private void loadProperties(JSONWriter json, Set<Object> allValues,
			IResource resource) throws JSONException {
		String SELECT_PROPERTIES = ISparqlConstants.PREFIX //
				+ "SELECT DISTINCT ?property " //
				+ "WHERE { " //
				+ "?resource ?property ?object" //
				+ "} ORDER BY ?property";

		IExtendedIterator<IProperty> properties = resource.getEntityManager()
				.createQuery(SELECT_PROPERTIES, includeInferred)
				.setParameter("resource", resource).evaluate(IProperty.class);

		json.key("data").array();
		for (IProperty property : properties) {
			loadValues(json, allValues, resource, property, true);
		}
		json.endArray();
	}

	private void loadPropertyValues(JSONWriter json, Set<Object> allValues,
			IResource resource, IProperty property) throws JSONException {
		json.key("data").array();
		loadValues(json, allValues, resource, property, false);
		json.endArray();
	}

	private void addPresentation(JSONWriter json, Set<Object> values)
			throws JSONException {
		json.key("presentation").object();
		for (Object value : values) {
			json.key(value.toString());
			json.object();

			addLabel(json, value);

			json.endObject();
		}
		json.endObject();
	}

	private void loadValues(JSONWriter json, Set<Object> allValues,
			IResource resource, IProperty property, boolean onlyFirstValue)
			throws JSONException {
		allValues.add(property);

		PropertyNode propertyNode = new PropertyNode(resource, property, true);

		Collection<IStatement> stmts;
		if (onlyFirstValue) {
			stmts = Collections.singleton(propertyNode.getFirstStatement());
		} else {
			stmts = propertyNode.getChildren();
		}

		json.object();
		json.key("subject").value(resource);
		json.key("predicate").value(property);
		json.key("objects").array();
		for (IStatement stmt : stmts) {
			allValues.add(stmt.getObject());
			valueToJson(json, stmt.getObject(), stmt.isInferred());
		}
		json.endArray();
		if (onlyFirstValue) {
			json.key("hasMoreValues").value(
					propertyNode.hasMultipleStatements());
		}
		json.endObject();
	}

	protected void valueToJson(JSONWriter json, Object value, boolean inferred)
			throws JSONException {
		json.object();
		json.key("nominalValue").value(value.toString());
		json.key("interfaceName");
		if (value instanceof IReference) {
			json.value(((IReference) value).getURI() == null ? "BlankNode"
					: "NamedNode");
		} else {
			json.value("Literal");
		}
		json.endObject();
	}

	protected void addLabel(JSONWriter json, Object object)
			throws JSONException {
		IItemLabelProvider itemLabelProvider = (IItemLabelProvider) adapterFactory
				.adapt(object, IItemLabelProvider.class);
		String label = null;
		String image = null;
		if (itemLabelProvider != null) {
			label = itemLabelProvider.getText(object);
			image = KommaHttpPlugin.createHttpUrl(String
					.valueOf(itemLabelProvider.getImage(object)));
		} else {
			label = object instanceof ILiteral ? ((ILiteral) object).getLabel()
					: object.toString();
		}

		if (label != null) {
			json.key("label").value(label);
		}
		if (image != null) {
			json.key("image").value(image);
		}
	}

	@Override
	public void refresh() {
		IAdapterFactory newAdapterFactory = getAdapterFactory();
		if (adapterFactory == null || !adapterFactory.equals(newAdapterFactory)) {
			adapterFactory = newAdapterFactory;
		}

		refreshBrowser();

		super.refresh();
	}

	private void refreshBrowser() {
		if (browserReady && isStale()) {
			browser.execute("refresh();");
		}
	}

	@Override
	public boolean setEditorInput(Object input) {
		if (input instanceof IModel) {
			input = ((IModel) input).getOntology();
		}
		if (input == null || input instanceof IObject) {
			setStale(true);
			// setStale(resource != input || resource != null &&
			// !resource.equals(input));
			resource = (IObject) input;
			return true;
		}
		return super.setEditorInput(input);
	}

	public void setInput(Object input) {
		setEditorInput(input);
	}
}
