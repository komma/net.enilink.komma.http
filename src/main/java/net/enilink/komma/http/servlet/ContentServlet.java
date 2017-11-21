/*******************************************************************************
 * Copyright (c) 2000, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package net.enilink.komma.http.servlet;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet to interface client with remote Eclipse
 */
public class ContentServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private EclipseConnector connector;

	/**
	 */
	public void init() throws ServletException {
		try {
			connector = new EclipseConnector(getServletContext());
		} catch (Throwable e) {
			throw new ServletException(e);
		}
	}

	/**
	 * Called by the server (via the <code>service</code> method) to allow a servlet to handle a GET request.
	 */
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// required for "org.eclipse.equinox.http" service
		if (getServletContext().getMajorVersion() > 2 || getServletContext().getMinorVersion() >= 4) {
			req.setCharacterEncoding("UTF-8"); //$NON-NLS-1$
		}
		// do wrap the request into one without query string and parameters
		// otherwise it might try to get a resource with it appended
		HttpServletRequestWrapper wrappedReq = new HttpServletRequestWrapper(req) {
			@Override
			public String getQueryString() {
				return null;
			}

			@Override
			public Enumeration<String> getParameterNames() {
				return Collections.emptyEnumeration();
			}

			@Override
			public Map<String, String[]> getParameterMap() {
				return Collections.emptyMap();
			}
		};
		if (connector != null) {
			connector.transfer(wrappedReq, resp);
		}
	}

	/**
	 * 
	 * Called by the server (via the <code>service</code> method) to allow a servlet to handle a POST request.
	 * 
	 * Handle the search requests,
	 * 
	 */
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if (connector != null)
			connector.transfer(req, resp);
	}
}
