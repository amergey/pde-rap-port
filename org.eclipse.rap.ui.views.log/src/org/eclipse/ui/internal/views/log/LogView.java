/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Jacek Pospychala <jacek.pospychala@pl.ibm.com> - bugs 202583, 202584, 207344
 *     													bugs 207323, 207931, 207101
 *     													bugs 172658, 216341, 216657
 *     Benjamin Cabe <benjamin.cabe@anyware-tech.com> - bug 218648 
 *     Tuukka Lehtonen <tuukka.lehtonen@semantum.fi>  - bug 247907
 *******************************************************************************/

package org.eclipse.ui.internal.views.log;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.action.*;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.util.Policy;
import org.eclipse.jface.viewers.*;
import org.eclipse.jface.window.Window;
import org.eclipse.osgi.util.NLS;
import org.eclipse.rwt.RWT;
import org.eclipse.rwt.service.IServiceHandler;
import org.eclipse.rwt.widgets.ExternalBrowser;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.dialogs.PatternFilter;
import org.eclipse.ui.part.ViewPart;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

public class LogView extends ViewPart implements ILogListener {
	public static final String DOWNLOAD_SERVICE_HANDLER_ID = "downloadLogFile"; //$NON-NLS-1$

	public static final String P_LOG_WARNING = "warning"; //$NON-NLS-1$
	public static final String P_LOG_ERROR = "error"; //$NON-NLS-1$
	public static final String P_LOG_INFO = "info"; //$NON-NLS-1$
	public static final String P_LOG_OK = "ok"; //$NON-NLS-1$
	public static final String P_LOG_LIMIT = "limit"; //$NON-NLS-1$
	public static final String P_USE_LIMIT = "useLimit"; //$NON-NLS-1$
	public static final String P_SHOW_ALL_SESSIONS = "allSessions"; //$NON-NLS-1$
	protected static final String P_COLUMN_1 = "column2"; //$NON-NLS-1$
	protected static final String P_COLUMN_2 = "column3"; //$NON-NLS-1$
	protected static final String P_COLUMN_3 = "column4"; //$NON-NLS-1$
	public static final String P_ACTIVATE = "activate"; //$NON-NLS-1$
	public static final String P_SHOW_FILTER_TEXT = "show_filter_text"; //$NON-NLS-1$
	public static final String P_ORDER_TYPE = "orderType"; //$NON-NLS-1$
	public static final String P_ORDER_VALUE = "orderValue"; //$NON-NLS-1$
	public static final String P_IMPORT_LOG = "importLog"; //$NON-NLS-1$
	public static final String P_GROUP_BY = "groupBy"; //$NON-NLS-1$

	private int MESSAGE_ORDER;
	private int PLUGIN_ORDER;
	private int DATE_ORDER;

	public final static byte MESSAGE = 0x0;
	public final static byte PLUGIN = 0x1;
	public final static byte DATE = 0x2;
	public static int ASCENDING = 1;
	public static int DESCENDING = -1;

	public static final int GROUP_BY_NONE = 0;
	public static final int GROUP_BY_SESSION = 1;
	public static final int GROUP_BY_PLUGIN = 2;

	private List elements;
	private Map groups;
	private LogSession currentSession;

	private List batchedEntries;
	private boolean batchEntries;

//	private Clipboard fClipboard;

	private IMemento fMemento;
	private File fInputFile;
	private String fDirectory;

	private Comparator fComparator;

	// hover text
	private boolean fCanOpenTextShell;
	private Text fTextLabel;
	private Shell fTextShell;

	private boolean fFirstEvent = true;

	private TreeColumn fColumn1;
	private TreeColumn fColumn2;
	private TreeColumn fColumn3;

	private Tree fTree;
	private FilteredTree fFilteredTree;
	private LogViewLabelProvider fLabelProvider;

	private Action fPropertiesAction;
	private Action fDeleteLogAction;
	private Action fReadLogAction;
//	private Action fCopyAction;
	private Action fActivateViewAction;
	private Action fOpenLogAction;
	private Action fExportLogAction;
	private Action fExportLogEntryAction;

	private DownloadServiceHandler fDownloadServiceHandler;

	/**
	 * Action called when user selects "Group by -> ..." from menu.
	 */
	class GroupByAction extends Action {
		private int groupBy;

		public GroupByAction(String text, int groupBy) {
			super(text, IAction.AS_RADIO_BUTTON);

			this.groupBy = groupBy;

			if (fMemento.getInteger(LogView.P_GROUP_BY).intValue() == groupBy) {
				setChecked(true);
			}
		}

		public void run() {
			if (fMemento.getInteger(LogView.P_GROUP_BY).intValue() != groupBy) {
				fMemento.putInteger(LogView.P_GROUP_BY, groupBy);
				reloadLog();
			}
		}
	}

	/**
	 * Constructor
	 */
	public LogView() {
		elements = new ArrayList();
		groups = new HashMap();
		batchedEntries = new ArrayList();
		fInputFile = Platform.getLogFileLocation().toFile();
		fDownloadServiceHandler = new DownloadServiceHandler(fInputFile);
		RWT.getServiceManager().registerServiceHandler(DOWNLOAD_SERVICE_HANDLER_ID, fDownloadServiceHandler);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.WorkbenchPart#createPartControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createPartControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		composite.setLayout(layout);
//		composite.setLayoutData(new GridData(GridData.FILL_BOTH));

		readLogFile();
		createViewer(composite);
		getSite().setSelectionProvider(fFilteredTree.getViewer());
		createActions();
//		fClipboard = new Clipboard(fTree.getDisplay());
		fTree.setToolTipText(""); //$NON-NLS-1$
		initializeViewerSorter();

		makeHoverShell();

		Platform.addLogListener(this);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(fFilteredTree, IHelpContextIds.LOG_VIEW);
		getSite().getWorkbenchWindow().addPerspectiveListener(new IPerspectiveListener2() {

			public void perspectiveChanged(IWorkbenchPage page, IPerspectiveDescriptor perspective, IWorkbenchPartReference partRef, String changeId) {
				if (!(partRef instanceof IViewReference))
					return;

				IWorkbenchPart part = partRef.getPart(false);
				if (part == null) {
					return;
				}

				if (part.equals(LogView.this)) {
					if (changeId.equals(IWorkbenchPage.CHANGE_VIEW_SHOW)) {
						if (!batchedEntries.isEmpty()) {
							pushBatchedEntries();
						}

						batchEntries = false;
					} else if (changeId.equals(IWorkbenchPage.CHANGE_VIEW_HIDE)) {
						batchEntries = true;
					}
				}
			}

			public void perspectiveActivated(IWorkbenchPage page, IPerspectiveDescriptor perspective) {
				// empty
			}

			public void perspectiveChanged(IWorkbenchPage page, IPerspectiveDescriptor perspective, String changeId) {
				// empty
			}

		});
	}

	/**
	 * Creates the actions for the viewsite action bars
	 */
	private void createActions() {
		IActionBars bars = getViewSite().getActionBars();

//		fCopyAction = createCopyAction();
//		bars.setGlobalActionHandler(ActionFactory.COPY.getId(), fCopyAction);

		IToolBarManager toolBarManager = bars.getToolBarManager();

		fExportLogAction = createExportLogAction();
		toolBarManager.add(fExportLogAction);

		fExportLogEntryAction = createExportLogEntryAction();

		final Action importLogAction = createImportLogAction();
		toolBarManager.add(importLogAction);

		toolBarManager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));

		final Action clearAction = createClearAction();
		toolBarManager.add(clearAction);

		fDeleteLogAction = createDeleteLogAction();
		toolBarManager.add(fDeleteLogAction);

		fOpenLogAction = createOpenLogAction();
		toolBarManager.add(fOpenLogAction);

		fReadLogAction = createReadLogAction();
		toolBarManager.add(fReadLogAction);

		toolBarManager.add(new Separator());

		IMenuManager mgr = bars.getMenuManager();

		mgr.add(createGroupByAction());
		mgr.add(new Separator());
		mgr.add(createFilterAction());
		mgr.add(new Separator());

		fActivateViewAction = createActivateViewAction();
		mgr.add(fActivateViewAction);
		if (fFilteredTree.getFilterControl() != null)
			mgr.add(createShowTextFilter());

		fPropertiesAction = createPropertiesAction();

		MenuManager popupMenuManager = new MenuManager("#PopupMenu"); //$NON-NLS-1$
		IMenuListener listener = new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
//				manager.add(fCopyAction);
//				manager.add(new Separator());
				manager.add(clearAction);
				manager.add(fDeleteLogAction);
				manager.add(fOpenLogAction);
				manager.add(fReadLogAction);
				manager.add(new Separator());
				manager.add(fExportLogAction);
				manager.add(createImportLogAction());
				manager.add(new Separator());
				manager.add(fExportLogEntryAction);
				manager.add(new Separator());

				((EventDetailsDialogAction) fPropertiesAction).setComparator(fComparator);
				TreeItem[] selection = fTree.getSelection();
				if ((selection.length > 0) && (selection[0].getData() instanceof LogEntry)) {
					manager.add(fPropertiesAction);
				}

				manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
			}
		};
		popupMenuManager.addMenuListener(listener);
		popupMenuManager.setRemoveAllWhenShown(true);
		getSite().registerContextMenu(popupMenuManager, getSite().getSelectionProvider());
		Menu menu = popupMenuManager.createContextMenu(fTree);
		fTree.setMenu(menu);
	}

	private Action createActivateViewAction() {
		Action action = new Action(Messages.get().LogView_activate) { //       	
			public void run() {
				fMemento.putString(P_ACTIVATE, isChecked() ? "true" : "false"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		};
		action.setChecked(fMemento.getString(P_ACTIVATE).equals("true")); //$NON-NLS-1$
		return action;
	}

	private Action createClearAction() {
		Action action = new Action(Messages.get().LogView_clear) {
			public void run() {
				handleClear();
			}
		};
		action.setImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_CLEAR));
		action.setDisabledImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_CLEAR_DISABLED));
		action.setToolTipText(Messages.get().LogView_clear_tooltip);
		action.setText(Messages.get().LogView_clear);
		return action;
	}

//	private Action createCopyAction() {
//		Action action = new Action(Messages.get().LogView_copy) {
//			public void run() {
//				copyToClipboard(fFilteredTree.getViewer().getSelection());
//			}
//		};
//		action.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_TOOL_COPY));
//		return action;
//	}

	private Action createDeleteLogAction() {
		Action action = new Action(Messages.get().LogView_delete) {
			public void run() {
				doDeleteLog();
			}
		};
		action.setToolTipText(Messages.get().LogView_delete_tooltip);
		action.setImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_REMOVE_LOG));
		action.setDisabledImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_REMOVE_LOG_DISABLED));
		action.setEnabled(fInputFile.exists() && fInputFile.equals(Platform.getLogFileLocation().toFile()));
		return action;
	}

	private Action createExportLogAction() {
		Action action = new Action(Messages.get().LogView_export) {
			public void run() {
				handleExport(true);
			}
		};
		action.setToolTipText(Messages.get().LogView_export_tooltip);
		action.setImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_EXPORT));
		action.setDisabledImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_EXPORT_DISABLED));
		action.setEnabled(fInputFile.exists());
		return action;
	}

	private Action createExportLogEntryAction() {
		Action action = new Action(Messages.get().LogView_exportEntry) {
			public void run() {
				handleExport(false);
			}
		};
		action.setToolTipText(Messages.get().LogView_exportEntry_tooltip);
		action.setImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_EXPORT));
		action.setDisabledImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_EXPORT_DISABLED));
		action.setEnabled(!fFilteredTree.getViewer().getSelection().isEmpty());
		return action;
	}

	private Action createFilterAction() {
		Action action = new Action(Messages.get().LogView_filter) {
			public void run() {
				handleFilter();
			}
		};
		action.setToolTipText(Messages.get().LogView_filter);
		action.setImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_FILTER));
		action.setDisabledImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_FILTER_DISABLED));
		return action;
	}

	private Action createImportLogAction() {
		Action action = new ImportLogAction(this, Messages.get().LogView_import, fMemento);
		action.setToolTipText(Messages.get().LogView_import_tooltip);
		action.setImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_IMPORT));
		action.setDisabledImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_IMPORT_DISABLED));
		return action;
	}

	private Action createOpenLogAction() {
		Action action = null;
		try {
			// TODO this isn't the best way to check... we should be smarter and use package admin
			// check to see if org.eclipse.ui.ide is available
			Class.forName("org.eclipse.ui.ide.IDE"); //$NON-NLS-1$
			// check to see if org.eclipse.core.filesystem is available
			Class.forName("org.eclipse.core.filesystem.IFileStore"); //$NON-NLS-1$
			action = new OpenIDELogFileAction(this);
		} catch (ClassNotFoundException e) {
			action = new Action() {
				public void run() {
					if (fInputFile.exists()) {
						Job job = getOpenLogFileJob();
						job.setUser(false);
						job.setPriority(Job.SHORT);
						job.schedule();
					}
				}
			};
		}
		action.setText(Messages.get().LogView_view_currentLog);
		action.setImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_OPEN_LOG));
		action.setDisabledImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_OPEN_LOG_DISABLED));
		action.setEnabled(fInputFile.exists());
		action.setToolTipText(Messages.get().LogView_view_currentLog_tooltip);
		return action;
	}

	private Action createPropertiesAction() {
		Action action = new EventDetailsDialogAction(fTree, fFilteredTree.getViewer(), fMemento);
		action.setImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_PROPERTIES));
		action.setDisabledImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_PROPERTIES_DISABLED));
		action.setToolTipText(Messages.get().LogView_properties_tooltip);
		action.setEnabled(false);
		return action;
	}

	private Action createReadLogAction() {
		Action action = new Action(Messages.get().LogView_readLog_restore) {
			public void run() {
				fInputFile = Platform.getLogFileLocation().toFile();
				reloadLog();
			}
		};
		action.setToolTipText(Messages.get().LogView_readLog_restore_tooltip);
		action.setImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_READ_LOG));
		action.setDisabledImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_READ_LOG_DISABLED));
		return action;
	}

	/**
	 * Creates the Show Text Filter view menu action 
	 * @return the new action for the Show Text Filter 
	 */
	private Action createShowTextFilter() {
		Action action = new Action(Messages.get().LogView_show_filter_text) {
			public void run() {
				showFilterText(isChecked());
			}
		};
		boolean visible = fMemento.getBoolean(P_SHOW_FILTER_TEXT).booleanValue();
		action.setChecked(visible);
		showFilterText(visible);
		return action;
	}

	/**
	 * Shows/hides the filter text control from the filtered tree. This method also sets the 
	 * P_SHOW_FILTER_TEXT preference to the visible state
	 * 
	 * @param visible if the filter text control should be shown or not
	 */
	private void showFilterText(boolean visible) {
		fMemento.putBoolean(P_SHOW_FILTER_TEXT, visible);

		Text filterControl = fFilteredTree.getFilterControl();
		Composite filterComposite = filterControl.getParent(); // FilteredTree new look lays filter Text on additional composite

		GridData gd = (GridData) filterComposite.getLayoutData();
		gd.exclude = !visible;
		filterComposite.setVisible(visible);

		// reset control if we aren't visible and if we get visible again
		filterControl.setText(Messages.get().LogView_show_filter_initialText);

		if (visible) {
			filterControl.selectAll();
			setFocus();
		}

		fFilteredTree.layout(false);
	}

	private IContributionItem createGroupByAction() {
		IMenuManager manager = new MenuManager(Messages.get().LogView_GroupBy);
		manager.add(new GroupByAction(Messages.get().LogView_GroupBySession, LogView.GROUP_BY_SESSION));
		manager.add(new GroupByAction(Messages.get().LogView_GroupByPlugin, LogView.GROUP_BY_PLUGIN));
		manager.add(new GroupByAction(Messages.get().LogView_GroupByNone, LogView.GROUP_BY_NONE));
		return manager;
	}

	private void createViewer(Composite parent) {
		PatternFilter filter = new PatternFilter() {
			protected boolean isLeafMatch(Viewer viewer, Object element) {
				if (element instanceof LogEntry) {
					LogEntry logEntry = (LogEntry) element;
					String message = logEntry.getMessage();
					String plugin = logEntry.getPluginId();
					DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, RWT.getLocale());
					String date = dateFormat.format(logEntry.getDate());
					return wordMatches(message) || wordMatches(plugin) || wordMatches(date);
				}
				return false;
			}
		};
		filter.setIncludeLeadingWildcard(true);
		fFilteredTree = new FilteredTree(parent, SWT.FULL_SELECTION, filter, true);
		// need to give filter Textbox some space from the border
		if (fFilteredTree.getFilterControl() != null) {
			Composite filterComposite = fFilteredTree.getFilterControl().getParent(); // FilteredTree new look lays filter Text on additional composite
			GridData gd = (GridData) filterComposite.getLayoutData();
			gd.verticalIndent = 2;
			gd.horizontalIndent = 1;
		}
		fFilteredTree.setLayoutData(new GridData(GridData.FILL_BOTH));
		fFilteredTree.setInitialText(Messages.get().LogView_show_filter_initialText);
		fTree = fFilteredTree.getViewer().getTree();
		fTree.setLinesVisible(true);
		createColumns(fTree);
		fFilteredTree.getViewer().setAutoExpandLevel(2);
		fFilteredTree.getViewer().setContentProvider(new LogViewContentProvider(this));
		fFilteredTree.getViewer().setLabelProvider(fLabelProvider = new LogViewLabelProvider(this));
		fLabelProvider.connect(this);
		fFilteredTree.getViewer().addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent e) {
				handleSelectionChanged(e.getSelection());
				if (fPropertiesAction.isEnabled())
					((EventDetailsDialogAction) fPropertiesAction).resetSelection();
			}
		});
		fFilteredTree.getViewer().addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				((EventDetailsDialogAction) fPropertiesAction).setComparator(fComparator);
				fPropertiesAction.run();
			}
		});
		fFilteredTree.getViewer().setInput(this);
		addMouseListeners();
		addDragSource();
	}

	private void createColumns(Tree tree) {
		fColumn1 = new TreeColumn(tree, SWT.LEFT);
		fColumn1.setText(Messages.get().LogView_column_message);
		fColumn1.setWidth(fMemento.getInteger(P_COLUMN_1).intValue());
		fColumn1.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				MESSAGE_ORDER *= -1;
				ViewerComparator comparator = getViewerComparator(MESSAGE);
				fFilteredTree.getViewer().setComparator(comparator);
				boolean isComparatorSet = ((EventDetailsDialogAction) fPropertiesAction).resetSelection(MESSAGE, MESSAGE_ORDER);
				setComparator(MESSAGE);
				if (!isComparatorSet)
					((EventDetailsDialogAction) fPropertiesAction).setComparator(fComparator);
				fMemento.putInteger(P_ORDER_VALUE, MESSAGE_ORDER);
				fMemento.putInteger(P_ORDER_TYPE, MESSAGE);
				setColumnSorting(fColumn1, MESSAGE_ORDER);
			}
		});

		fColumn2 = new TreeColumn(tree, SWT.LEFT);
		fColumn2.setText(Messages.get().LogView_column_plugin);
		fColumn2.setWidth(fMemento.getInteger(P_COLUMN_2).intValue());
		fColumn2.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				PLUGIN_ORDER *= -1;
				ViewerComparator comparator = getViewerComparator(PLUGIN);
				fFilteredTree.getViewer().setComparator(comparator);
				boolean isComparatorSet = ((EventDetailsDialogAction) fPropertiesAction).resetSelection(PLUGIN, PLUGIN_ORDER);
				setComparator(PLUGIN);
				if (!isComparatorSet)
					((EventDetailsDialogAction) fPropertiesAction).setComparator(fComparator);
				fMemento.putInteger(P_ORDER_VALUE, PLUGIN_ORDER);
				fMemento.putInteger(P_ORDER_TYPE, PLUGIN);
				setColumnSorting(fColumn2, PLUGIN_ORDER);
			}
		});

		fColumn3 = new TreeColumn(tree, SWT.LEFT);
		fColumn3.setText(Messages.get().LogView_column_date);
		fColumn3.setWidth(fMemento.getInteger(P_COLUMN_3).intValue());
		fColumn3.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				DATE_ORDER *= -1;
				ViewerComparator comparator = getViewerComparator(DATE);
				fFilteredTree.getViewer().setComparator(comparator);
				setComparator(DATE);
				((EventDetailsDialogAction) fPropertiesAction).setComparator(fComparator);
				fMemento.putInteger(P_ORDER_VALUE, DATE_ORDER);
				fMemento.putInteger(P_ORDER_TYPE, DATE);
				setColumnSorting(fColumn3, DATE_ORDER);
			}
		});

		tree.setHeaderVisible(true);
	}

	private void initializeViewerSorter() {
		byte orderType = fMemento.getInteger(P_ORDER_TYPE).byteValue();
		ViewerComparator comparator = getViewerComparator(orderType);
		fFilteredTree.getViewer().setComparator(comparator);
		if (orderType == MESSAGE)
			setColumnSorting(fColumn1, MESSAGE_ORDER);
		else if (orderType == PLUGIN)
			setColumnSorting(fColumn2, PLUGIN_ORDER);
		else if (orderType == DATE)
			setColumnSorting(fColumn3, DATE_ORDER);
	}

	private void setColumnSorting(TreeColumn column, int order) {
		fTree.setSortColumn(column);
		fTree.setSortDirection(order == ASCENDING ? SWT.UP : SWT.DOWN);
	}

	public void dispose() {
		writeSettings();
		Platform.removeLogListener(this);
//		fClipboard.dispose();
		if (fTextShell != null)
			fTextShell.dispose();
		fLabelProvider.disconnect(this);
		fFilteredTree.dispose();
		super.dispose();
	}

	/**
	 * Import log from file selected in FileDialog.
	 */
	void handleImport() {
		FileDialog dialog = new FileDialog(getViewSite().getShell());
		dialog.setFilterExtensions(new String[] {"*.log"}); //$NON-NLS-1$
		if (fDirectory != null)
			dialog.setFilterPath(fDirectory);
		String path = dialog.open();
		if (path == null) { // cancel
			return;
		}

		File file = new Path(path).toFile();
		if (file.exists()) {
			handleImportPath(path);
		} else {
			String msg = NLS.bind(Messages.get().LogView_FileCouldNotBeFound, file.getName());
			MessageDialog.openError(getViewSite().getShell(), Messages.get().LogView_OpenFile, msg);
		}
	}

	/**
	 * Import log from given file path. Do nothing if file not exists.
	 * @param path path to log file.
	 */
	public void handleImportPath(String path) {
		File file = new File(path);
		if (path != null && file.exists()) {
			setLogFile(file);
		}
	}

	/**
	 * Import log from given file path.
	 * @param path path to log file.
	 */
	protected void setLogFile(File path) {
		fInputFile = path;
		fDirectory = fInputFile.getParent();
		IRunnableWithProgress op = new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) {
				monitor.beginTask(Messages.get().LogView_operation_importing, IProgressMonitor.UNKNOWN);
				readLogFile();
			}
		};
		ProgressMonitorDialog pmd = new ProgressMonitorDialog(getViewSite().getShell());
		try {
			pmd.run(true, true, op);
		} catch (InvocationTargetException e) { // do nothing
		} catch (InterruptedException e) { // do nothing
		} finally {
			fReadLogAction.setText(Messages.get().LogView_readLog_reload);
			fReadLogAction.setToolTipText(Messages.get().LogView_readLog_reload);
			asyncRefresh(false);
			resetDialogButtons();
		}
	}

	private void handleExport(boolean exportWholeLog) {
//		FileDialog dialog = new FileDialog(getViewSite().getShell(), SWT.SAVE);
//		dialog.setFilterExtensions(new String[] {"*.log"}); //$NON-NLS-1$
//		if (fDirectory != null)
//			dialog.setFilterPath(fDirectory);
//		String path = dialog.open();
//		if (path != null) {
//			if (path.indexOf('.') == -1 && !path.endsWith(".log")) //$NON-NLS-1$
//				path += ".log"; //$NON-NLS-1$
//			File outputFile = new Path(path).toFile();
//			fDirectory = outputFile.getParent();
//			if (outputFile.exists()) {
//				String message = NLS.bind(Messages.get().LogView_confirmOverwrite_message, outputFile.toString());
//				if (!MessageDialog.openQuestion(getViewSite().getShell(), (exportWholeLog ? Messages.get().LogView_exportLog : Messages.get().LogView_exportLogEntry), message))
//					return;
//			}
//
//			BufferedReader in = null;
//			BufferedWriter out = null;
//			try {
//				out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8")); //$NON-NLS-1$
//				if (exportWholeLog)
//					in = new BufferedReader(new InputStreamReader(new FileInputStream(fInputFile), "UTF-8")); //$NON-NLS-1$
//				else {
//					String selectedEntryAsString = selectionToString(fFilteredTree.getViewer().getSelection());
//					in = new BufferedReader(new StringReader(selectedEntryAsString));
//				}
//				copy(in, out);
//			} catch (IOException ex) {
//				// do nothing
//			} finally {
//				try {
//					if (in != null)
//						in.close();
//				} catch (IOException e) {
//					// do nothing
//				}
//				try {
//					if (out != null)
//						out.close();
//				} catch (IOException e) {
//					// do nothing
//				}
//			}
//		}
		StringBuilder url = new StringBuilder();
		url.append(RWT.getRequest().getContextPath());
		url.append(RWT.getRequest().getServletPath());
		url.append('?');
		url.append(IServiceHandler.REQUEST_PARAM);
		url.append('=').append(DOWNLOAD_SERVICE_HANDLER_ID);
		String encodedURL = RWT.getResponse().encodeURL(url.toString());

		ExternalBrowser.open(DOWNLOAD_SERVICE_HANDLER_ID, encodedURL, SWT.NONE);
	}

//	private void copy(BufferedReader reader, BufferedWriter writer) throws IOException {
//		String line;
//		while (reader.ready() && ((line = reader.readLine()) != null)) {
//			writer.write(line);
//			writer.newLine();
//		}
//	}

	private void handleFilter() {
		FilterDialog dialog = new FilterDialog(getSite().getShell(), fMemento);
		dialog.create();
		dialog.getShell().setText(Messages.get().LogView_FilterDialog_title);
		if (dialog.open() == Window.OK)
			reloadLog();
	}

	private void doDeleteLog() {
		String title = Messages.get().LogView_confirmDelete_title;
		String message = Messages.get().LogView_confirmDelete_message;
		if (!MessageDialog.openConfirm(fTree.getShell(), title, message))
			return;
		if (fInputFile.delete() || elements.size() > 0) {
			handleClear();
		}
	}

	public void fillContextMenu(IMenuManager manager) { // nothing
	}

	public AbstractEntry[] getElements() {
		return (AbstractEntry[]) elements.toArray(new AbstractEntry[elements.size()]);
	}

	protected void handleClear() {
		BusyIndicator.showWhile(fTree.getDisplay(), new Runnable() {
			public void run() {
				elements.clear();
				groups.clear();
				if (currentSession != null) {
					currentSession.removeAllChildren();
				}
				asyncRefresh(false);
				resetDialogButtons();
			}
		});
	}

	/**
	 * Reloads the log
	 */
	protected void reloadLog() {
		IRunnableWithProgress op = new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) {
				monitor.beginTask(Messages.get().LogView_operation_reloading, IProgressMonitor.UNKNOWN);
				readLogFile();
			}
		};
		ProgressMonitorDialog pmd = new ProgressMonitorDialog(getViewSite().getShell());
		try {
			pmd.run(true, true, op);
		} catch (InvocationTargetException e) { // do nothing
		} catch (InterruptedException e) { // do nothing
		} finally {
			fReadLogAction.setText(Messages.get().LogView_readLog_restore);
			fReadLogAction.setToolTipText(Messages.get().LogView_readLog_restore);
			asyncRefresh(false);
			resetDialogButtons();
		}
	}

	/**
	 * Reads the chosen backing log file
	 */
	void readLogFile() {
		elements.clear();
		groups.clear();

		List result = new ArrayList();
		LogSession lastLogSession = LogReader.parseLogFile(fInputFile, result, fMemento);
		if ((lastLogSession != null) && isEclipseStartTime(lastLogSession.getDate())) {
			currentSession = lastLogSession;
		} else {
			currentSession = null;
		}

		group(result);
		limitEntriesCount();

		getSite().getShell().getDisplay().asyncExec(new Runnable() {
			public void run() {
				setContentDescription(getTitleSummary());
			}
		});

	}

	private boolean isEclipseStartTime(Date date) {
		String ts = System.getProperty("eclipse.startTime"); //$NON-NLS-1$
		try {
			return (ts != null && date != null && date.getTime() == Long.parseLong(ts));
		} catch (NumberFormatException e) {
			// empty
		}
		return false;
	}

	private String getTitleSummary() {
		String path = ""; //$NON-NLS-1$
		try {
			path = fInputFile.getCanonicalPath();
		} catch (IOException e) { // log nothing
		}

		if (isPlatformLogOpen()) {
			return Messages.get().LogView_WorkspaceLogFile;
		}

		Map sources = LogFilesManager.getLogSources();
		if (sources.containsValue(path)) {
			for (Iterator i = sources.keySet().iterator(); i.hasNext();) {
				String key = (String) i.next();
				if (sources.get(key).equals(path)) {
					return NLS.bind(Messages.get().LogView_LogFileTitle, new String[] {key, path});
				}
			}
		}

		return path;
	}

	/**
	 * Add new entries to correct groups in the view.
	 * @param entries new entries to show up in groups in the view.
	 */
	private void group(List entries) {
		if (fMemento.getInteger(P_GROUP_BY).intValue() == GROUP_BY_NONE) {
			elements.addAll(entries);
		} else {
			for (Iterator i = entries.iterator(); i.hasNext();) {
				LogEntry entry = (LogEntry) i.next();
				Group group = getGroup(entry);
				group.addChild(entry);
			}
		}
	}

	/**
	 * Limits the number of entries according to the max entries limit set in
	 * memento.
	 */
	private void limitEntriesCount() {
		int limit = Integer.MAX_VALUE;
		if (fMemento.getString(LogView.P_USE_LIMIT).equals("true")) {//$NON-NLS-1$
			limit = fMemento.getInteger(LogView.P_LOG_LIMIT).intValue();
		}

		int entriesCount = getEntriesCount();

		if (entriesCount <= limit) {
			return;
		}
		Comparator dateComparator = new Comparator() {
			public int compare(Object o1, Object o2) {
				Date l1 = ((LogEntry) o1).getDate();
				Date l2 = ((LogEntry) o2).getDate();
				if ((l1 != null) && (l2 != null)) {
					return l1.before(l2) ? -1 : 1;
				} else if ((l1 == null) && (l2 == null)) {
					return 0;
				} else
					return (l1 == null) ? -1 : 1;
			}
		};

		if (fMemento.getInteger(P_GROUP_BY).intValue() == GROUP_BY_NONE) {
			elements.subList(0, elements.size() - limit).clear();
		} else {
			List copy = new ArrayList(entriesCount);
			for (Iterator i = elements.iterator(); i.hasNext();) {
				AbstractEntry group = (AbstractEntry) i.next();
				copy.addAll(Arrays.asList(group.getChildren(group)));
			}

			Collections.sort(copy, dateComparator);
			List toRemove = copy.subList(0, copy.size() - limit);

			for (Iterator i = elements.iterator(); i.hasNext();) {
				AbstractEntry group = (AbstractEntry) i.next();
				group.removeChildren(toRemove);
			}
		}

	}

	private int getEntriesCount() {
		if (fMemento.getInteger(P_GROUP_BY).intValue() == GROUP_BY_NONE) {
			return elements.size();
		}
		int size = 0;
		for (Iterator i = elements.iterator(); i.hasNext();) {
			AbstractEntry group = (AbstractEntry) i.next();
			size += group.size();
		}
		return size;
	}

	/**
	 * Returns group appropriate for the entry. Group depends on P_GROUP_BY
	 * preference, or is null if grouping is disabled (GROUP_BY_NONE), or group
	 * could not be determined. May create group if it haven't existed before.
	 * 
	 * @param entry entry to be grouped
	 * @return group or null if grouping is disabled
	 */
	protected Group getGroup(LogEntry entry) {
		int groupBy = fMemento.getInteger(P_GROUP_BY).intValue();
		Object elementGroupId = null;
		String groupName = null;

		switch (groupBy) {
			case GROUP_BY_PLUGIN :
				groupName = entry.getPluginId();
				elementGroupId = groupName;
				break;

			case GROUP_BY_SESSION :
				elementGroupId = entry.getSession();
				break;

			default : // grouping is disabled
				return null;
		}

		if (elementGroupId == null) { // could not determine group
			return null;
		}

		Group group = (Group) groups.get(elementGroupId);
		if (group == null) {
			if (groupBy == GROUP_BY_SESSION) {
				group = entry.getSession();
			} else {
				group = new Group(groupName);
			}
			groups.put(elementGroupId, group);
			elements.add(group);
		}

		return group;
	}

	public void logging(IStatus status, String plugin) {
		if (!isPlatformLogOpen())
			return;

		if (batchEntries) {
			// create LogEntry immediately to don't loose IStatus creation date.
			LogEntry entry = createLogEntry(status);
			batchedEntries.add(entry);
			return;
		}

		if (fFirstEvent || (currentSession == null)) {
			readLogFile();
			asyncRefresh(true);
			fFirstEvent = false;
		} else {
			LogEntry entry = createLogEntry(status);

			if (!batchedEntries.isEmpty()) {
				// batch new entry as well, to have only one asyncRefresh()
				batchedEntries.add(entry);
				pushBatchedEntries();
			} else {
				pushEntry(entry);
				asyncRefresh(true);
			}
		}
	}

	/**
	 * Push batched entries to log view.
	 */
	private void pushBatchedEntries() {
		Job job = new Job(Messages.get().LogView_AddingBatchedEvents) {
			protected IStatus run(IProgressMonitor monitor) {
				for (int i = 0; i < batchedEntries.size(); i++) {
					if (!monitor.isCanceled()) {
						LogEntry entry = (LogEntry) batchedEntries.get(i);
						pushEntry(entry);
						batchedEntries.remove(i);
					}
				}
				asyncRefresh(true);
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}

	private LogEntry createLogEntry(IStatus status) {
		LogEntry entry = new LogEntry(status);
		entry.setSession(currentSession);
		return entry;
	}

	private synchronized void pushEntry(LogEntry entry) {
		if (LogReader.isLogged(entry, fMemento)) {
			group(Collections.singletonList(entry));
			limitEntriesCount();
		}
		asyncRefresh(true);
	}

	private void asyncRefresh(final boolean activate) {
		if (fTree.isDisposed())
			return;
		Display display = fTree.getDisplay();
		final ViewPart view = this;
		if (display != null) {
			display.asyncExec(new Runnable() {
				public void run() {
					if (!fTree.isDisposed()) {
						TreeViewer viewer = fFilteredTree.getViewer();
						viewer.refresh();
						viewer.expandToLevel(2);
						fDeleteLogAction.setEnabled(fInputFile.exists() && fInputFile.equals(Platform.getLogFileLocation().toFile()));
						fOpenLogAction.setEnabled(fInputFile.exists());
						fExportLogAction.setEnabled(fInputFile.exists());
						fExportLogEntryAction.setEnabled(!viewer.getSelection().isEmpty());
						if (activate && fActivateViewAction.isChecked()) {
							IWorkbenchPage page = Activator.getDefault().getWorkbench().getActiveWorkbenchWindow().getActivePage();
							if (page != null)
								page.bringToTop(view);
						}
					}
				}
			});
		}
	}

	public void setFocus() {
		if (fFilteredTree != null) {
			if (fMemento.getBoolean(P_SHOW_FILTER_TEXT).booleanValue()) {
				Text filterControl = fFilteredTree.getFilterControl();
				if (filterControl != null && !filterControl.isDisposed()) {
					filterControl.setFocus();
				}
			} else if (!fFilteredTree.isDisposed()) {
				fFilteredTree.setFocus();
			}
		}
	}

	private void handleSelectionChanged(ISelection selection) {
		updateStatus(selection);
//		fCopyAction.setEnabled((!selection.isEmpty()) && ((IStructuredSelection) selection).getFirstElement() != null);
		fPropertiesAction.setEnabled(!selection.isEmpty());
		fExportLogEntryAction.setEnabled(!selection.isEmpty());
	}

	private void updateStatus(ISelection selection) {
		IStatusLineManager status = getViewSite().getActionBars().getStatusLineManager();
		if (selection.isEmpty())
			status.setMessage(null);
		else {
			Object element = ((IStructuredSelection) selection).getFirstElement();
			status.setMessage(((LogViewLabelProvider) fFilteredTree.getViewer().getLabelProvider()).getColumnText(element, 0));
		}
	}

	/**
	 * Converts selected log view element to string.
	 * @return textual log entry representation or null if selection doesn't contain log entry
	 */
	private static String selectionToString(ISelection selection) {
		StringWriter writer = new StringWriter();
		PrintWriter pwriter = new PrintWriter(writer);
		if (selection.isEmpty())
			return null;
		AbstractEntry entry = (AbstractEntry) ((IStructuredSelection) selection).getFirstElement();
		entry.write(pwriter);
		pwriter.flush();
		String textVersion = writer.toString();
		pwriter.close();
		try {
			writer.close();
		} catch (IOException e) {
			// empty
		}

		return textVersion;
	}

//	/**
//	 * Copies selected element to clipboard.
//	 */
//	private void copyToClipboard(ISelection selection) {
//		String textVersion = selectionToString(selection);
//		if ((textVersion != null) && (textVersion.trim().length() > 0)) {
//			// set the clipboard contents
//			fClipboard.setContents(new Object[] {textVersion}, new Transfer[] {TextTransfer.getInstance()});
//		}
//	}

	public void init(IViewSite site, IMemento memento) throws PartInitException {
		super.init(site, memento);
		if (memento == null)
			this.fMemento = XMLMemento.createWriteRoot("LOGVIEW"); //$NON-NLS-1$
		else
			this.fMemento = memento;
		readSettings();

		// initialize column ordering 
		final byte type = this.fMemento.getInteger(P_ORDER_TYPE).byteValue();
		switch (type) {
			case DATE :
				DATE_ORDER = this.fMemento.getInteger(P_ORDER_VALUE).intValue();
				MESSAGE_ORDER = DESCENDING;
				PLUGIN_ORDER = DESCENDING;
				break;
			case MESSAGE :
				MESSAGE_ORDER = this.fMemento.getInteger(P_ORDER_VALUE).intValue();
				DATE_ORDER = DESCENDING;
				PLUGIN_ORDER = DESCENDING;
				break;
			case PLUGIN :
				PLUGIN_ORDER = this.fMemento.getInteger(P_ORDER_VALUE).intValue();
				MESSAGE_ORDER = DESCENDING;
				DATE_ORDER = DESCENDING;
				break;
			default :
				DATE_ORDER = DESCENDING;
				MESSAGE_ORDER = DESCENDING;
				PLUGIN_ORDER = DESCENDING;
		}
		setComparator(fMemento.getInteger(P_ORDER_TYPE).byteValue());
	}

	private void initializeMemento() {
		if (fMemento.getString(P_USE_LIMIT) == null) {
			fMemento.putString(P_USE_LIMIT, "true"); //$NON-NLS-1$
		}
		if (fMemento.getInteger(P_LOG_LIMIT) == null) {
			fMemento.putInteger(P_LOG_LIMIT, 50);
		}
		if (fMemento.getString(P_LOG_INFO) == null) {
			fMemento.putString(P_LOG_INFO, "true"); //$NON-NLS-1$
		}
		if (fMemento.getString(P_LOG_OK) == null) {
			fMemento.putString(P_LOG_OK, "true"); //$NON-NLS-1$
		}
		if (fMemento.getString(P_LOG_WARNING) == null) {
			fMemento.putString(P_LOG_WARNING, "true"); //$NON-NLS-1$
		}
		if (fMemento.getString(P_LOG_ERROR) == null) {
			fMemento.putString(P_LOG_ERROR, "true"); //$NON-NLS-1$
		}
		if (fMemento.getString(P_SHOW_ALL_SESSIONS) == null) {
			fMemento.putString(P_SHOW_ALL_SESSIONS, "true"); //$NON-NLS-1$
		}
	}

	public void saveState(IMemento memento) {
		if (this.fMemento == null || memento == null)
			return;
		//store some sane values to prevent the view from being broken
		this.fMemento.putInteger(P_COLUMN_1, getColumnWidth(fColumn1, 300));
		this.fMemento.putInteger(P_COLUMN_2, getColumnWidth(fColumn2, 150));
		this.fMemento.putInteger(P_COLUMN_3, getColumnWidth(fColumn3, 150));
		this.fMemento.putString(P_ACTIVATE, fActivateViewAction.isChecked() ? "true" : "false"); //$NON-NLS-1$ //$NON-NLS-2$
		memento.putMemento(this.fMemento);
		writeSettings();
	}

	/**
	 * Returns the width of the column or the default value if the column has been resized to be not visible
	 * @param column the column to get the width from
	 * @param defaultwidth the width to return if the column has been resized to not be visible
	 * @return the width of the column or the default value
	 * 
	 * @since 3.6
	 */
	int getColumnWidth(TreeColumn column, int defaultwidth) {
		int width = column.getWidth();
		return width < 1 ? defaultwidth : width;
	}

	private void addMouseListeners() {
		Listener tableListener = new Listener() {
			public void handleEvent(Event e) {
				switch (e.type) {
//					case SWT.MouseExit :
//					case SWT.MouseMove :
//						onMouseMove(e);
//						break;
//					case SWT.MouseHover :
//						onMouseHover(e);
//						break;
					case SWT.MouseDown :
						onMouseDown(e);
						break;
				}
			}
		};
		int[] tableEvents = new int[] {SWT.MouseDown/*, SWT.MouseMove, SWT.MouseHover, SWT.MouseExit*/};
		for (int i = 0; i < tableEvents.length; i++) {
			fTree.addListener(tableEvents[i], tableListener);
		}
	}

	/**
	 * Adds drag source support to error log tree.
	 */
	private void addDragSource() {
		DragSource source = new DragSource(fTree, DND.DROP_COPY);
		Transfer[] types = new Transfer[] {TextTransfer.getInstance()};
		source.setTransfer(types);

		source.addDragListener(new DragSourceAdapter() {

			public void dragStart(DragSourceEvent event) {
				ISelection selection = fFilteredTree.getViewer().getSelection();
				if (selection.isEmpty()) {
					event.doit = false;
					return;
				}

				AbstractEntry entry = (AbstractEntry) ((TreeSelection) selection).getFirstElement();
				if (!(entry instanceof LogEntry)) {
					event.doit = false;
					return;
				}
			}

			public void dragSetData(DragSourceEvent event) {
				if (!TextTransfer.getInstance().isSupportedType(event.dataType)) {
					return;
				}

				ISelection selection = fFilteredTree.getViewer().getSelection();
				String textVersion = selectionToString(selection);
				event.data = textVersion;
			}
		});
	}

	private void makeHoverShell() {
		// parent it off the workbench window's shell so it will be valid regardless of whether the view is a detached window or not
		fTextShell = new Shell(getSite().getWorkbenchWindow().getShell(), SWT.NO_FOCUS | SWT.ON_TOP | SWT.TOOL);
		Display display = fTextShell.getDisplay();
		fTextShell.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
		GridLayout layout = new GridLayout(1, false);
		int border = ((fTree.getShell().getStyle() & SWT.NO_TRIM) == 0) ? 0 : 1;
		layout.marginHeight = border;
		layout.marginWidth = border;
		fTextShell.setLayout(layout);
		fTextShell.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		Composite shellComposite = new Composite(fTextShell, SWT.NONE);
		layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		shellComposite.setLayout(layout);
		shellComposite.setLayoutData(new GridData(GridData.FILL_BOTH | GridData.VERTICAL_ALIGN_BEGINNING));
		fTextLabel = new Text(shellComposite, SWT.WRAP | SWT.MULTI | SWT.READ_ONLY);
		GridData gd = new GridData(GridData.FILL_BOTH);
		gd.widthHint = 100;
		gd.grabExcessHorizontalSpace = true;
		fTextLabel.setLayoutData(gd);
		Color c = fTree.getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND);
		fTextLabel.setBackground(c);
		c = fTree.getDisplay().getSystemColor(SWT.COLOR_INFO_FOREGROUND);
		fTextLabel.setForeground(c);
		fTextLabel.setEditable(false);
		fTextShell.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				onTextShellDispose(e);
			}
		});
	}

	void onTextShellDispose(DisposeEvent e) {
		fCanOpenTextShell = true;
		setFocus();
	}

	void onMouseDown(Event e) {
		if (fTextShell != null && !fTextShell.isDisposed() && !fTextShell.isFocusControl()) {
			fTextShell.setVisible(false);
			fCanOpenTextShell = true;
		}
	}

	void onMouseHover(Event e) {
		if (!fCanOpenTextShell || fTextShell == null || fTextShell.isDisposed())
			return;
		fCanOpenTextShell = false;
		Point point = new Point(e.x, e.y);
		TreeItem item = fTree.getItem(point);
		if (item == null)
			return;

		String message = null;
		if (item.getData() instanceof LogEntry) {
			message = ((LogEntry) item.getData()).getStack();
		} else if (item.getData() instanceof LogSession) {
			LogSession session = ((LogSession) item.getData());
			message = Messages.get().LogView_SessionStarted;
			if (session.getDate() != null) {
				DateFormat formatter = new SimpleDateFormat(LogEntry.F_DATE_FORMAT);
				message += formatter.format(session.getDate());
			}
		}

		if (message == null)
			return;

		fTextLabel.setText(message);
		Rectangle bounds = fTree.getDisplay().getBounds();
		Point cursorPoint = fTree.getDisplay().getCursorLocation();
		int x = point.x;
		int y = point.y + 25;
		int width = fTree.getColumn(0).getWidth();
		int height = 125;
		if (cursorPoint.x + width > bounds.width)
			x -= width;
		if (cursorPoint.y + height + 25 > bounds.height)
			y -= height + 27;

		fTextShell.setLocation(fTree.toDisplay(x, y));
		fTextShell.setSize(width, height);
		fTextShell.setVisible(true);
	}

	void onMouseMove(Event e) {
		if (fTextShell != null && !fTextShell.isDisposed() && fTextShell.isVisible())
			fTextShell.setVisible(false);

		Point point = new Point(e.x, e.y);
		TreeItem item = fTree.getItem(point);
		if (item == null)
			return;
		Image image = item.getImage();
		Object data = item.getData();
		if (data instanceof LogEntry) {
			LogEntry entry = (LogEntry) data;
			int parentCount = getNumberOfParents(entry);
			int startRange = 20 + Math.max(image.getBounds().width + 2, 7 + 2) * parentCount;
			int endRange = startRange + 16;
			fCanOpenTextShell = e.x >= startRange && e.x <= endRange;
		}
	}

	private int getNumberOfParents(AbstractEntry entry) {
		AbstractEntry parent = (AbstractEntry) entry.getParent(entry);
		if (parent == null)
			return 0;
		return 1 + getNumberOfParents(parent);
	}

	public Comparator getComparator() {
		return fComparator;
	}

	private void setComparator(byte sortType) {
		if (sortType == DATE) {
			fComparator = new Comparator() {
				public int compare(Object e1, Object e2) {
					long date1 = 0;
					long date2 = 0;
					if ((e1 instanceof LogEntry) && (e2 instanceof LogEntry)) {
						date1 = ((LogEntry) e1).getDate().getTime();
						date2 = ((LogEntry) e2).getDate().getTime();
					} else if ((e1 instanceof LogSession) && (e2 instanceof LogSession)) {
						date1 = ((LogSession) e1).getDate() == null ? 0 : ((LogSession) e1).getDate().getTime();
						date2 = ((LogSession) e2).getDate() == null ? 0 : ((LogSession) e2).getDate().getTime();
					}
					if (date1 == date2) {
						int result = elements.indexOf(e2) - elements.indexOf(e1);
						if (DATE_ORDER == DESCENDING)
							result *= DESCENDING;
						return result;
					}
					if (DATE_ORDER == DESCENDING)
						return date1 > date2 ? DESCENDING : ASCENDING;
					return date1 < date2 ? DESCENDING : ASCENDING;
				}
			};
		} else if (sortType == PLUGIN) {
			fComparator = new Comparator() {
				public int compare(Object e1, Object e2) {
					if ((e1 instanceof LogEntry) && (e2 instanceof LogEntry)) {
						LogEntry entry1 = (LogEntry) e1;
						LogEntry entry2 = (LogEntry) e2;
						return getDefaultComparator().compare(entry1.getPluginId(), entry2.getPluginId()) * PLUGIN_ORDER;
					}
					return 0;
				}
			};
		} else {
			fComparator = new Comparator() {
				public int compare(Object e1, Object e2) {
					if ((e1 instanceof LogEntry) && (e2 instanceof LogEntry)) {
						LogEntry entry1 = (LogEntry) e1;
						LogEntry entry2 = (LogEntry) e2;
						return getDefaultComparator().compare(entry1.getMessage(), entry2.getMessage()) * MESSAGE_ORDER;
					}
					return 0;
				}
			};
		}
	}

	private Comparator getDefaultComparator() {
		return Policy.getComparator();
	}

	private ViewerComparator getViewerComparator(byte sortType) {
		if (sortType == PLUGIN) {
			return new ViewerComparator() {
				public int compare(Viewer viewer, Object e1, Object e2) {
					if ((e1 instanceof LogEntry) && (e2 instanceof LogEntry)) {
						LogEntry entry1 = (LogEntry) e1;
						LogEntry entry2 = (LogEntry) e2;
						return getComparator().compare(entry1.getPluginId(), entry2.getPluginId()) * PLUGIN_ORDER;
					}
					return 0;
				}
			};
		} else if (sortType == MESSAGE) {
			return new ViewerComparator() {
				public int compare(Viewer viewer, Object e1, Object e2) {
					if ((e1 instanceof LogEntry) && (e2 instanceof LogEntry)) {
						LogEntry entry1 = (LogEntry) e1;
						LogEntry entry2 = (LogEntry) e2;
						return getComparator().compare(entry1.getMessage(), entry2.getMessage()) * MESSAGE_ORDER;
					}
					return 0;
				}
			};
		} else {
			return new ViewerComparator() {
				private int indexOf(Object[] array, Object o) {
					if (o == null)
						return -1;
					for (int i = 0; i < array.length; ++i)
						if (o.equals(array[i]))
							return i;
					return -1;
				}

				public int compare(Viewer viewer, Object e1, Object e2) {
					long date1 = 0;
					long date2 = 0;
					if ((e1 instanceof LogEntry) && (e2 instanceof LogEntry)) {
						date1 = ((LogEntry) e1).getDate().getTime();
						date2 = ((LogEntry) e2).getDate().getTime();
					} else if ((e1 instanceof LogSession) && (e2 instanceof LogSession)) {
						date1 = ((LogSession) e1).getDate() == null ? 0 : ((LogSession) e1).getDate().getTime();
						date2 = ((LogSession) e2).getDate() == null ? 0 : ((LogSession) e2).getDate().getTime();
					}

					if (date1 == date2) {
						// Everything that appears in LogView should be an AbstractEntry.
						AbstractEntry parent = (AbstractEntry) ((AbstractEntry) e1).getParent(null);
						Object[] children = null;
						if (parent != null)
							children = parent.getChildren(parent);

						int result = 0;
						if (children != null) {
							// The elements in children seem to be in reverse order,
							// i.e. latest log message first, therefore index(e2)-index(e1)
							result = indexOf(children, e2) - indexOf(children, e1);
						} else {
							result = elements.indexOf(e1) - elements.indexOf(e2);
						}
						if (DATE_ORDER == DESCENDING)
							result *= DESCENDING;
						return result;
					}
					if (DATE_ORDER == DESCENDING)
						return date1 > date2 ? DESCENDING : ASCENDING;
					return date1 < date2 ? DESCENDING : ASCENDING;
				}
			};
		}
	}

	private void resetDialogButtons() {
		((EventDetailsDialogAction) fPropertiesAction).resetDialogButtons();
	}

	/**
	 * Returns the filter dialog settings object used to maintain
	 * state between filter dialogs
	 * @return the dialog settings to be used
	 */
	private IDialogSettings getLogSettings() {
		IDialogSettings settings = Activator.getDefault().getDialogSettings();
		return settings.getSection(getClass().getName());
	}

	/**
	 * Returns the plugin preferences used to maintain
	 * state of log view
	 * @return the plugin preferences
	 */
	private Preferences getLogPreferences() {
		return (new InstanceScope()).getNode(Activator.PLUGIN_ID);
	}

	/**
	 * Loads any saved {@link IDialogSettings} into the backing view memento
	 */
	private void readSettings() {
		IDialogSettings s = getLogSettings();
		if (s == null) {
			initializeMemento();
		} else {
			fMemento.putString(P_USE_LIMIT, s.getBoolean(P_USE_LIMIT) ? "true" : "false"); //$NON-NLS-1$ //$NON-NLS-2$
			fMemento.putString(P_LOG_INFO, s.getBoolean(P_LOG_INFO) ? "true" : "false"); //$NON-NLS-1$ //$NON-NLS-2$
			fMemento.putString(P_LOG_OK, s.getBoolean(P_LOG_OK) ? "true" : "false"); //$NON-NLS-1$ //$NON-NLS-2$
			fMemento.putString(P_LOG_WARNING, s.getBoolean(P_LOG_WARNING) ? "true" : "false"); //$NON-NLS-1$ //$NON-NLS-2$
			fMemento.putString(P_LOG_ERROR, s.getBoolean(P_LOG_ERROR) ? "true" : "false"); //$NON-NLS-1$ //$NON-NLS-2$
			fMemento.putString(P_SHOW_ALL_SESSIONS, s.getBoolean(P_SHOW_ALL_SESSIONS) ? "true" : "false"); //$NON-NLS-1$ //$NON-NLS-2$
			try {
				fMemento.putInteger(P_LOG_LIMIT, s.getInt(P_LOG_LIMIT));
			} catch (NumberFormatException e) {
				fMemento.putInteger(P_LOG_LIMIT, 50);
			}
		}

		Preferences p = getLogPreferences(); // never returns null
		fMemento.putInteger(P_COLUMN_1, getColumnWidthPreference(p, P_COLUMN_1, 300));
		fMemento.putInteger(P_COLUMN_2, getColumnWidthPreference(p, P_COLUMN_2, 150));
		fMemento.putInteger(P_COLUMN_3, getColumnWidthPreference(p, P_COLUMN_3, 150));
		fMemento.putBoolean(P_ACTIVATE, p.getBoolean(P_ACTIVATE, true));
		fMemento.putInteger(P_ORDER_VALUE, p.getInt(P_ORDER_VALUE, DESCENDING));
		fMemento.putInteger(P_ORDER_TYPE, p.getInt(P_ORDER_TYPE, LogView.DATE));
		fMemento.putBoolean(P_SHOW_FILTER_TEXT, p.getBoolean(P_SHOW_FILTER_TEXT, true));
		fMemento.putInteger(P_GROUP_BY, p.getInt(P_GROUP_BY, LogView.GROUP_BY_NONE));
	}

	/**
	 * Returns the width to use for the column represented by the given key. The default width
	 * is returned iff:
	 * <ul>
	 * <li>There is no preference for the given key</li>
	 * <li>The returned preference value is too small, making the columns invisible by width.</li>
	 * </ul>
	 * @param preferences
	 * @param key
	 * @param defaultwidth
	 * @return the stored width for the a column described by the given key or the default width
	 * 
	 * @since 3.6
	 */
	int getColumnWidthPreference(Preferences preferences, String key, int defaultwidth) {
		int width = preferences.getInt(key, defaultwidth);
		return width < 1 ? defaultwidth : width;
	}

	private void writeSettings() {
		writeViewSettings();
		writeFilterSettings();
	}

	private void writeFilterSettings() {
		IDialogSettings settings = getLogSettings();
		if (settings == null)
			settings = Activator.getDefault().getDialogSettings().addNewSection(getClass().getName());
		settings.put(P_USE_LIMIT, fMemento.getString(P_USE_LIMIT).equals("true")); //$NON-NLS-1$
		settings.put(P_LOG_LIMIT, fMemento.getInteger(P_LOG_LIMIT).intValue());
		settings.put(P_LOG_INFO, fMemento.getString(P_LOG_INFO).equals("true")); //$NON-NLS-1$
		settings.put(P_LOG_OK, fMemento.getString(P_LOG_OK).equals("true")); //$NON-NLS-1$
		settings.put(P_LOG_WARNING, fMemento.getString(P_LOG_WARNING).equals("true")); //$NON-NLS-1$
		settings.put(P_LOG_ERROR, fMemento.getString(P_LOG_ERROR).equals("true")); //$NON-NLS-1$
		settings.put(P_SHOW_ALL_SESSIONS, fMemento.getString(P_SHOW_ALL_SESSIONS).equals("true")); //$NON-NLS-1$
	}

	private void writeViewSettings() {
		Preferences preferences = getLogPreferences();
		preferences.putInt(P_COLUMN_1, fMemento.getInteger(P_COLUMN_1).intValue());
		preferences.putInt(P_COLUMN_2, fMemento.getInteger(P_COLUMN_2).intValue());
		preferences.putInt(P_COLUMN_3, fMemento.getInteger(P_COLUMN_3).intValue());
		preferences.putBoolean(P_ACTIVATE, fMemento.getBoolean(P_ACTIVATE).booleanValue());
		preferences.putInt(P_ORDER_VALUE, fMemento.getInteger(P_ORDER_VALUE).intValue());
		preferences.putInt(P_ORDER_TYPE, fMemento.getInteger(P_ORDER_TYPE).intValue());
		preferences.putBoolean(P_SHOW_FILTER_TEXT, fMemento.getBoolean(P_SHOW_FILTER_TEXT).booleanValue());
		preferences.putInt(P_GROUP_BY, fMemento.getInteger(P_GROUP_BY).intValue());
		try {
			preferences.flush();
		} catch (BackingStoreException e) {
			// empty
		}
	}

	public void sortByDateDescending() {
		setColumnSorting(fColumn3, DESCENDING);
	}

	protected Job getOpenLogFileJob() {
		final Shell shell = getViewSite().getShell();
		return new Job(Messages.get().OpenLogDialog_message) {
			protected IStatus run(IProgressMonitor monitor) {
				boolean failed = false;
				if (fInputFile.length() <= LogReader.MAX_FILE_LENGTH) {
//					failed = !Program.launch(fInputFile.getAbsolutePath());
//					if (failed) {
//						Program p = Program.findProgram(".txt"); //$NON-NLS-1$
//						if (p != null) {
//							p.execute(fInputFile.getAbsolutePath());
//							return Status.OK_STATUS;
//						}
//					}
				}
				if (failed) {
					final OpenLogDialog openDialog = new OpenLogDialog(shell, fInputFile);
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							openDialog.create();
							openDialog.open();
						}
					});
				}
				return Status.OK_STATUS;
			}
		};
	}

	protected File getLogFile() {
		return fInputFile;
	}

	/**
	 * Returns whether given session equals to currently displayed in LogView.
	 * @param session LogSession
	 * @return true if given session equals to currently displayed in LogView
	 */
	public boolean isCurrentLogSession(LogSession session) {
		return isPlatformLogOpen() && (currentSession != null) && (currentSession.equals(session));
	}

	/**
	 * Returns whether currently open log is platform log or imported file.
	 * @return true if currently open log is platform log, false otherwise
	 */
	public boolean isPlatformLogOpen() {
		return (fInputFile.equals(Platform.getLogFileLocation().toFile()));
	}

	/**
	 * 
	 */
	public void setPlatformLog() {
		setLogFile(Platform.getLogFileLocation().toFile());
	}
}
