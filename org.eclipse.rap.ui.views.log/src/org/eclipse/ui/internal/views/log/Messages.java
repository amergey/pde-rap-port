/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Jacek Pospychala <jacek.pospychala@pl.ibm.com> - bugs 202583, 207344
 *     Benjamin Cabe <benjamin.cabe@anyware-tech.com> - bug 218648 
 *******************************************************************************/
package org.eclipse.ui.internal.views.log;

import org.eclipse.rwt.RWT;

public class Messages {

	public String LogView_column_message;
	public String LogView_column_plugin;
	public String LogView_column_date;
	public String LogView_clear;
	public String LogView_clear_tooltip;
	public String LogView_copy;
	public String LogView_delete;
	public String LogView_delete_tooltip;
	public String LogView_export;
	public String LogView_exportLog;
	public String LogView_export_tooltip;
	public String LogView_exportEntry;
	public String LogView_exportLogEntry;
	public String LogView_exportEntry_tooltip;
	public String LogView_import;
	public String LogView_import_tooltip;
	public String LogView_filter;
	public String LogView_readLog_reload;
	public String LogView_readLog_restore;
	public String LogView_readLog_restore_tooltip;
	public String LogView_show_filter_text;
	public String LogView_show_filter_initialText;

	public String LogView_SessionStarted;
	public String LogView_severity_error;
	public String LogView_severity_warning;
	public String LogView_severity_info;
	public String LogView_severity_ok;
	public String LogView_confirmDelete_title;
	public String LogView_confirmDelete_message;
	public String LogView_confirmOverwrite_message;
	public String LogView_operation_importing;
	public String LogView_operation_reloading;
	public String LogView_activate;
	public String LogView_AddingBatchedEvents;
	public String LogView_view_currentLog;
	public String LogView_view_currentLog_tooltip;
	public String LogView_properties_tooltip;

	public String LogView_FileCouldNotBeFound;
	public String LogView_FilterDialog_title;
	public String LogView_FilterDialog_eventTypes;
	public String LogView_FilterDialog_information;
	public String LogView_FilterDialog_warning;
	public String LogView_FilterDialog_error;
	public String LogView_FilterDialog_limitTo;
	public String LogView_FilterDialog_eventsLogged;
	public String LogView_FilterDialog_allSessions;
	public String LogView_FilterDialog_ok;
	public String LogView_FilterDialog_recentSession;
	public String LogView_GroupBy;
	public String LogView_GroupByNone;
	public String LogView_GroupByPlugin;
	public String LogView_GroupBySession;
	public String LogView_LogFileTitle;
	public String LogView_OpenFile;
	public String LogView_WorkspaceLogFile;

	public String LogViewLabelProvider_Session;
	public String LogViewLabelProvider_truncatedMessage;

	public String EventDetailsDialog_title;
	public String EventDetailsDialog_date;
	public String EventDetailsDialog_severity;
	public String EventDetailsDialog_message;
	public String EventDetailsDialog_exception;
	public String EventDetailsDialog_session;
	public String EventDetailsDialog_noStack;
	public String EventDetailsDialog_previous;
	public String EventDetailsDialog_next;
	public String EventDetailsDialog_copy;
	public String EventDetailsDialog_FilterDialog;
	public String EventDetailsDialog_ShowFilterDialog;

	public String FilterDialog_Add;
	public String FilterDialog_AddFilterTitle;
	public String FilterDialog_AddFliterLabel;
	public String FilterDialog_EnableFiltersCheckbox;
	public String FilterDialog_FilterShouldntContainSemicolon;
	public String FilterDialog_Remove;

	public String OpenLogDialog_title;
	public String OpenLogDialog_message;
	public String OpenLogDialog_cannotDisplay;

	public String ImportLogAction_noLaunchHistory;
	public String ImportLogAction_reloadWorkspaceLog;

	private static final String BUNDLE_NAME = "org.eclipse.ui.internal.views.log.messages"; //$NON-NLS-1$

	public static Messages get() {
		Class clazz = Messages.class;
		Object result = RWT.NLS.getISO8859_1Encoded(BUNDLE_NAME, clazz);
		return (Messages) result;
	}

}
