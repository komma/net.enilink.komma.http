/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package net.enilink.komma.http.servlet;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.Locale;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.enilink.komma.http.KommaHttpPlugin;

/**
 * Performs transfer of data from eclipse to a jsp/servlet
 */
public class EclipseConnector {
	public interface INotFoundCallout {
		public void notFound(String url);
	}

	private static final String errorPageBegin = "<!DOCTYPE html>\n" //$NON-NLS-1$
			+ "<html><head>\n" //$NON-NLS-1$
			+ "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n" //$NON-NLS-1$
			+ "</head>\n" //$NON-NLS-1$
			+ "<body><p>\n"; //$NON-NLS-1$
	private static final String errorPageEnd = "</p></body></html>"; //$NON-NLS-1$
	private static final IFilter allFilters[] = new IFilter[] {};

	private static final IFilter errorPageFilters[] = new IFilter[] {};

	private ServletContext context;
	private static INotFoundCallout notFoundCallout = null; // For JUnit Testing

	public EclipseConnector(ServletContext context) {
		this.context = context;
	}

	public void transfer(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		try {
			String url = getURL(req);
			if (url == null)
				return;
			//System.out.println("Transfer " + url); //$NON-NLS-1$
			// Redirect if the request includes PLUGINS_ROOT and is not a
			// content request
			String lowerCaseuRL = url.toLowerCase(Locale.ENGLISH);
			if (lowerCaseuRL.startsWith("jar:") //$NON-NLS-1$
					|| lowerCaseuRL.startsWith("platform:") //$NON-NLS-1$
					|| lowerCaseuRL.startsWith("file:")) { //$NON-NLS-1$
				int i = url.indexOf('?');
				if (i != -1)
					url = url.substring(0, i);
			}

			URLConnection con = createConnection(req, resp, url);

			InputStream is;
			boolean pageNotFound = false;
			try {
				is = con.getInputStream();
			} catch (IOException ioe) {
				pageNotFound = true;
				if (notFoundCallout != null) {
					notFoundCallout.notFound(url);
				}

				if (requiresErrorPage(lowerCaseuRL)) {
					// Try to load the error page if defined
					// TODO lookup error page
					String errorPage = ""; //$NON-NLS-1$
					if (errorPage != null && errorPage.length() > 0) {
						con = createConnection(req, resp, "help:" + errorPage); //$NON-NLS-1$
						resp.setContentType("text/html"); //$NON-NLS-1$
						try {
							is = con.getInputStream();
						} catch (IOException ioe2) {
							// Cannot open error page
							resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
							return;
						}
					} else {
						// Error page not defined
						resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
						return;
					}
				} else {
					// Non HTML file
					resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
					return;
				}
			} catch (Exception e) {
				// if it's a wrapped exception, unwrap it
				Throwable t = e;
				if (t instanceof UndeclaredThrowableException
						&& t.getCause() != null) {
					t = t.getCause();
				}

				StringBuffer message = new StringBuffer();
				message.append(errorPageBegin);
				message.append("<p>"); //$NON-NLS-1$
				message.append("The content producer for this document encountered an internal error while processing the document.");
				message.append("</p>"); //$NON-NLS-1$
				message.append("<pre>"); //$NON-NLS-1$
				Writer writer = new StringWriter();
				t.printStackTrace(new PrintWriter(writer));
				message.append(writer.toString());
				message.append("</pre>"); //$NON-NLS-1$
				message.append(errorPageEnd);

				is = new ByteArrayInputStream(message.toString().getBytes(
						"UTF8")); //$NON-NLS-1$
			}

			OutputStream out = resp.getOutputStream();
			IFilter filters[] = pageNotFound ? errorPageFilters : allFilters;
			// required for "org.eclipse.equinox.http" service
			if (context.getMajorVersion() > 2 || context.getMinorVersion() >= 4) {
				if (isProcessingRequired(resp.getContentType())) {
					for (int i = 0; i < filters.length; i++) {
						out = filters[i].filter(req, out);
					}
				}
			}

			transferContent(is, out);
			try {
				out.close();
			} catch (IOException ioe) {
				// Bug 314324 - do not report an error
			}
			is.close();

		} catch (Exception e) {
			String msg = "Error processing resource request " + getURL(req); //$NON-NLS-1$
			// KommaHttpPlugin.logError(msg, e);
		}
	}

	private boolean requiresErrorPage(String lowerCaseuRL) {
		return lowerCaseuRL.endsWith("htm") //$NON-NLS-1$
				|| lowerCaseuRL.endsWith("pdf") //$NON-NLS-1$
				|| lowerCaseuRL.endsWith("xhtml") //$NON-NLS-1$
				|| lowerCaseuRL.endsWith("shtml") //$NON-NLS-1$
				|| lowerCaseuRL.endsWith("html"); //$NON-NLS-1$
	}

	private boolean isProcessingRequired(String contentType) {
		if ("application/xhtml+xml".equals(contentType)) { //$NON-NLS-1$
			return true;
		}
		if (contentType == null || !contentType.startsWith("text")) { //$NON-NLS-1$
			return false;
		}
		if ("text/css".equals(contentType)) { //$NON-NLS-1$
			return false;
		}
		if ("text/javascript".equals(contentType)) { //$NON-NLS-1$
			return false;
		}
		return true;
	}

	private URLConnection createConnection(HttpServletRequest req,
			HttpServletResponse resp, String url) throws Exception {
		URLConnection con;
		con = openConnection(url, req, resp);
		String contentType;
		// use the context to get the mime type where possible
		String pathInfo = req.getPathInfo();
		String mimeType = context.getMimeType(pathInfo);
		if (useMimeType(req, mimeType)) {
			contentType = mimeType;
		} else {
			contentType = con.getContentType();
		}

		resp.setContentType(contentType);

		long maxAge = 0;
		try {
			// getExpiration() throws NullPointerException when URL is
			// jar:file:...
			long expiration = con.getExpiration();
			maxAge = (expiration - System.currentTimeMillis()) / 1000;
			if (maxAge < 0)
				maxAge = 0;
		} catch (Exception e) {
		}
		resp.setHeader("Cache-Control", "max-age=" + maxAge); //$NON-NLS-1$ //$NON-NLS-2$
		return con;
	}

	private boolean useMimeType(HttpServletRequest req, String mimeType) {
		if (mimeType == null) {
			return false;
		}
		//		if (mimeType.equals("application/xhtml+xml") && !UrlUtil.isMozilla(req)) { //$NON-NLS-1$
		// return false;
		// }
		return true;
	}

	/**
	 * Write the body to the response
	 */
	private void transferContent(InputStream inputStream, OutputStream out)
			throws IOException {
		try {
			// Prepare the input stream for reading
			BufferedInputStream dataStream = new BufferedInputStream(
					inputStream);

			// Create a fixed sized buffer for reading.
			// We could create one with the size of availabe data...
			byte[] buffer = new byte[4096];
			int len = 0;
			while (true) {
				len = dataStream.read(buffer); // Read file into the byte array
				if (len == -1)
					break;
				out.write(buffer, 0, len);
			}
		} catch (Exception e) {
		}
	}

	/**
	 * Gets content from the named url (this could be and eclipse defined url)
	 */
	private URLConnection openConnection(String url,
			HttpServletRequest request, HttpServletResponse response)
			throws Exception {

		URLConnection con = null;

		URL normalizedURL;
		if (url.startsWith("jar:")) { //$NON-NLS-1$
			// fix for bug 83929
			int excl = url.indexOf("!/"); //$NON-NLS-1$
			String jar = url.substring(0, excl);
			String path = url.length() > excl + 2 ? url.substring(excl + 2)
					: ""; //$NON-NLS-1$
			url = jar.replaceAll("!", "%21") + "!/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					+ path.replaceAll("!", "%21"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		normalizedURL = new URL(url);

		String protocol = normalizedURL.getProtocol();
		if (!("file".equals(protocol) //$NON-NLS-1$
				|| "platform".equals(protocol) //$NON-NLS-1$
				|| "jar".equals(protocol) || "bundleentry".equals(protocol) || "bundleresource".equals(protocol))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			throw new IOException();
		}

		con = normalizedURL.openConnection();

		con.setAllowUserInteraction(false);
		con.setDoInput(true);
		con.connect();
		return con;
	}

	/**
	 * Extracts the url from a request
	 */
	private String getURL(HttpServletRequest req) {
		String url = req.getPathInfo();
		if (url == null || url.length() == 0) {
			return null;
		}

		String query = ""; //$NON-NLS-1$
		boolean firstParam = true;
		for (Enumeration<?> params = req.getParameterNames(); params
				.hasMoreElements();) {
			String param = (String) params.nextElement();
			String[] values = req.getParameterValues(param);
			if (values == null)
				continue;
			for (int i = 0; i < values.length; i++) {
				if (firstParam) {
					query += "?" + param + "=" + values[i]; //$NON-NLS-1$ //$NON-NLS-2$
					firstParam = false;
				} else
					query += "&" + param + "=" + values[i]; //$NON-NLS-1$ //$NON-NLS-2$
			}
		}

		url += query;
		if (url.startsWith("/")) //$NON-NLS-1$
			url = url.substring(1);
		return url;
	}

	public static void setNotFoundCallout(INotFoundCallout callout) {
		notFoundCallout = callout;
	}
}