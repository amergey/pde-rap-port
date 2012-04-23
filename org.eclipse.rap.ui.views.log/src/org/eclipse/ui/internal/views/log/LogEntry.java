/*******************************************************************************
 * Copyright (c) 2000, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Jacek Pospychala <jacek.pospychala@pl.ibm.com> - bugs 209474, 207344
 *******************************************************************************/
package org.eclipse.ui.internal.views.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.*;
import java.util.*;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.rwt.RWT;

/**
 * Represents a given entry in the Error view
 */
public class LogEntry extends AbstractEntry {

	public static final String SPACE = " "; //$NON-NLS-1$
	public static final String F_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"; //$NON-NLS-1$
	private static final DateFormat GREGORIAN_SDF = new SimpleDateFormat(F_DATE_FORMAT, Locale.ENGLISH);
	private DateFormat localSDF;

	private String pluginId;
	private int severity;
	private int code;
	private String fDateString;
	private Date fDate;
	private String message;
	private String stack;
	private LogSession session;

	/**
	 * Constructor
	 */
	public LogEntry() {
		//do nothing
		localSDF = new SimpleDateFormat(F_DATE_FORMAT, RWT.getLocale());
	}

	/**
	 * Constructor - creates a new entry from the given status
	 * @param status an existing status to create a new entry from
	 */
	public LogEntry(IStatus status) {
		processStatus(status);
	}

	/**
	 * Returns the {@link LogSession} for this entry or the parent {@link LogSession}
	 * iff:
	 * <ul>
	 * <li>The session is <code>null</code> for this entry</li>
	 * <li>The parent of this entry is not <code>null</code> and is a {@link LogEntry}</li>
	 * </ul>
	 * @return the {@link LogSession} for this entry
	 */
	public LogSession getSession() {
		if ((session == null) && (parent != null) && (parent instanceof LogEntry)) {
			return ((LogEntry) parent).getSession();
		}
		return session;
	}

	/**
	 * Sets the {@link LogSession} for this entry. No validation is done on the new session.
	 * @param session the session to set.
	 */
	void setSession(LogSession session) {
		this.session = session;
	}

	/**
	 * Returns the severity of this entry.
	 * @return the severity
	 * @see IStatus#OK
	 * @see IStatus#WARNING
	 * @see IStatus#INFO
	 * @see IStatus#ERROR
	 */
	public int getSeverity() {
		return severity;
	}

	/**
	 * Returns if the severity of this entry is {@link IStatus#OK}
	 * @return if the entry is OK or not
	 */
	public boolean isOK() {
		return severity == IStatus.OK;
	}

	/**
	 * Returns the code for this entry
	 * @return the code for this entry
	 */
	public int getCode() {
		return code;
	}

	/**
	 * Returns the id of the plugin that generated this entry
	 * @return the plugin id of this entry
	 */
	public String getPluginId() {
		return pluginId;
	}

	/**
	 * Returns the message for this entry or <code>null</code> if there is no message
	 * @return the message or <code>null</code>
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Returns the stack trace for this entry or <code>null</code> if there is no stack trace
	 * @return the stack trace or <code>null</code>
	 */
	public String getStack() {
		return stack;
	}

	/**
	 * Returns a pretty-print formatting for the date for this entry
	 * @return the formatted date for this entry
	 */
	public String getFormattedDate() {
		if (fDateString == null) {
			fDateString = localSDF.format(getDate());
		}
		return fDateString;
	}

	/**
	 * Returns the date for this entry or the epoch if the current date value is <code>null</code>
	 * @return the entry date or the epoch if there is no date entry
	 */
	public Date getDate() {
		if (fDate == null) {
			fDate = new Date(0); // unknown date - return epoch
		}
		return fDate;
	}

	/**
	 * Returns the human-readable text representation of the integer
	 * severity value or '<code>?</code>' if the severity is unknown.
	 * @return the text representation of the severity
	 */
	public String getSeverityText() {
		switch (severity) {
			case IStatus.ERROR : {
				return Messages.get().LogView_severity_error;
			}
			case IStatus.WARNING : {
				return Messages.get().LogView_severity_warning;
			}
			case IStatus.INFO : {
				return Messages.get().LogView_severity_info;
			}
			case IStatus.OK : {
				return Messages.get().LogView_severity_ok;
			}
		}
		return "?"; //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return getSeverityText();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.internal.views.log.AbstractEntry#getLabel(java.lang.Object)
	 */
	public String getLabel(Object obj) {
		return getSeverityText();
	}

	/**
	 * Processes a given line from the log file
	 * @param line
	 * @throws ParseException
	 */
	public void processEntry(String line) throws ParseException {
		//!ENTRY <pluginID> <severity> <code> <date>
		//!ENTRY <pluginID> <date> if logged by the framework!!!
		StringTokenizer stok = new StringTokenizer(line, SPACE);
		severity = 0;
		code = 0;
		StringBuffer dateBuffer = new StringBuffer();
		int tokens = stok.countTokens();
		String token = null;
		for (int i = 0; i < tokens; i++) {
			token = stok.nextToken();
			switch (i) {
				case 0 : {
					break;
				}
				case 1 : {
					pluginId = token;
					break;
				}
				case 2 : {
					try {
						severity = Integer.parseInt(token);
					} catch (NumberFormatException nfe) {
						appendToken(dateBuffer, token);
					}
					break;
				}
				case 3 : {
					try {
						code = Integer.parseInt(token);
					} catch (NumberFormatException nfe) {
						appendToken(dateBuffer, token);
					}
					break;
				}
				default : {
					appendToken(dateBuffer, token);
				}
			}
		}
		Date date = GREGORIAN_SDF.parse(dateBuffer.toString());
		if (date != null) {
			fDate = date;
			fDateString = localSDF.format(fDate);
		}
	}

	/**
	 * Adds the given token to the given buffer, adding a space as needed
	 * @param buffer
	 * @param token
	 * 
	 * @since 3.6
	 */
	void appendToken(StringBuffer buffer, String token) {
		if (buffer.length() > 0) {
			buffer.append(SPACE);
		}
		buffer.append(token);
	}

	/**
	 * Processes the given sub-entry from the log
	 * @param line
	 * @return the depth of the sub-entry
	 * @throws ParseException
	 */
	public int processSubEntry(String line) throws ParseException {
		//!SUBENTRY <depth> <pluginID> <severity> <code> <date>
		//!SUBENTRY  <depth> <pluginID> <date>if logged by the framework!!!
		StringTokenizer stok = new StringTokenizer(line, SPACE);
		StringBuffer dateBuffer = new StringBuffer();
		int depth = 0;
		String token = null;
		int tokens = stok.countTokens();
		for (int i = 0; i < tokens; i++) {
			token = stok.nextToken();
			switch (i) {
				case 0 : {
					break;
				}
				case 1 : {
					depth = Integer.parseInt(token);
					break;
				}
				case 2 : {
					pluginId = token;
					break;
				}
				case 3 : {
					try {
						severity = Integer.parseInt(token);
					} catch (NumberFormatException nfe) {
						appendToken(dateBuffer, token);
					}
					break;
				}
				case 4 : {
					try {
						code = Integer.parseInt(token);
					} catch (NumberFormatException nfe) {
						appendToken(dateBuffer, token);
					}
					break;
				}
				default : {
					appendToken(dateBuffer, token);
				}
			}
		}
		Date date = GREGORIAN_SDF.parse(dateBuffer.toString());
		if (date != null) {
			fDate = date;
			fDateString = localSDF.format(fDate);
		}
		return depth;
	}

	/**
	 * Sets the stack to the given stack value. 
	 * No validation is performed on the new value.
	 * @param stack
	 */
	void setStack(String stack) {
		this.stack = stack;
	}

	/**
	 * Sets the message to the given message value.
	 * No validation is performed on the new value
	 * @param message
	 */
	void setMessage(String message) {
		this.message = message;
	}

	/**
	 * Process the given status and sub-statuses to fill this entry
	 * @param status
	 */
	private void processStatus(IStatus status) {
		pluginId = status.getPlugin();
		severity = status.getSeverity();
		code = status.getCode();
		fDate = new Date();
		fDateString = localSDF.format(fDate);
		message = status.getMessage();
		Throwable throwable = status.getException();
		if (throwable != null) {
			StringWriter swriter = new StringWriter();
			PrintWriter pwriter = new PrintWriter(swriter);
			throwable.printStackTrace(pwriter);
			pwriter.flush();
			pwriter.close();
			stack = swriter.toString();
		}
		IStatus[] schildren = status.getChildren();
		if (schildren.length > 0) {
			for (int i = 0; i < schildren.length; i++) {
				addChild(new LogEntry(schildren[i]));
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.internal.views.log.AbstractEntry#write(java.io.PrintWriter)
	 */
	public void write(PrintWriter writer) {
		if (session != null) {
			writer.println(session.getSessionData());
		}
		writer.println(getSeverityText());
		if (fDate != null) {
			writer.println(getDate());
		}
		if (message != null) {
			writer.println(getMessage());
		}
		if (stack != null) {
			writer.println();
			writer.println(stack);
		}
	}
}
