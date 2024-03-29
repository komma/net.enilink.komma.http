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

import java.io.*;

import javax.servlet.http.*;

/**
 * Filter for filtering out of documents delivered to the client
 * @since 3.4
 */
public interface IFilter {
	/**
	 * Filters OutputStream out
	 * 
	 * @param req
	 *            HTTPServletRequest for resource being filtered; filter's logic
	 *            might differ depending on the request
	 * @param out
	 *            original OutputStream
	 * @return filtered OutputStream
	 */
	OutputStream filter(HttpServletRequest req, OutputStream out);
}
