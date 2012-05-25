/*******************************************************************************
 * Copyright (c) 2005, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM - Initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.runtime;

import org.eclipse.rwt.RWT;


public class PDERuntimeMessages {

  private static final String BUNDLE_NAME = "org.eclipse.pde.internal.runtime.pderuntimeresources";//$NON-NLS-1$
  public String ActiveFormEditorSection_Active_Form_Page;
  public String ActiveMenuSection_0;
  public String ActiveMenuSection_1;
  public String ActiveMenuSection_2;
  public String ActiveMenuSection_3;
  public String ActiveMenuSection_4;
  public String ActiveMenuSection_5;
  public String ActiveMenuSection_6;
  public String ActiveMenuSection_7;
  public String RegistryView_refresh_label;
  public String RegistryView_refresh_tooltip;
  public String RegistryView_collapseAll_label;
  public String RegistryView_collapseAll_tooltip;
  public String RegistryView_folders_imports;
  public String RegistryView_folders_libraries;
  public String RegistryView_folders_extensionPoints;
  public String RegistryView_folders_extensions;
  public String RegistryView_found_problems;
  public String RegistryView_showRunning_label;
  public String RegistryView_showDisabled_label;
  public String RegistryView_showAdvanced_label;
  public String RegistryView_titleSummary;
  public String RegistryView_startAction_label;
  public String RegistryView_stopAction_label;
  public String RegistryView_enableAction_label;
  public String RegistryView_diag_dialog_title;
  public String RegistryView_diagnoseAction_label;
  public String RegistryView_disableAction_label;
  public String RegistryView_no_unresolved_constraints;
  public String MessageHelper_missing_optional_required_bundle;
  public String MessageHelper_missing_required_bundle;
  public String MessageHelper_missing_imported_package;
  public String MessageHelper_missing_host;
  public String SpyDialog_title;
  public String MenuSpyDialog_title;
  public String SpyDialog_close;
  public String SpyDialog_activeShell_title;
  public String SpyDialog_activeShell_desc;
  public String SpyDialog_activePart_title;
  public String SpyDialog_activePart_desc;
  public String SpyDialog_activeWizard_title;
  public String SpyDialog_activeWizard_desc;
  public String SpyDialog_activeMenuIds;
  public String SpyDialog_contributingPluginId_title;
  public String SpyDialog_contributingPluginId_desc;
  public String SpyDialog_activeSelection_title;
  public String SpyDialog_activeSelection_desc;
  public String SpyDialog_activeSelectionInterfaces_desc;
  public String SpyDialog_activeSelectedElementsCount_desc;
  public String SpyDialog_activeSelectedElement_desc;
  public String SpyDialog_activeSelectedElementInterfaces_desc;
  public String SpyDialog_activeDialogPageSection_title;
  public String SpyDialog_activeDialogPageSection_title2;
  public String SpyDialog_activeDialogPageSection_desc;
  public String SpyDialog_activeHelpSection_title;
  public String SpyDialog_activeHelpSection_desc;
  public String SpyIDEUtil_noSourceFound_title;
  public String SpyIDEUtil_noSourceFound_message;
  public String SpyDialog_activePageBook_title;
  public String SpyFormToolkit_saveImageAs_title;
  public String SpyFormToolkit_copyQualifiedName;
  public String RegistryBrowser_Bundle;
  public String RegistryBrowser_copy_label;
  public String RegistryBrowser_ExtensionPoint;
  public String RegistryBrowser_extensionPoints;
  public String RegistryBrowser_GroupBy;
  public String RegistryBrowser_InitializingView;
  public String RegistryBrowser_plugins;
  public String RegistryBrowser_Service;
  public String RegistryBrowser_Services;
  public String RegistryBrowserLabelProvider_contributedBy;
  public String RegistryBrowserLabelProvider_ExportedPackages;
  public String RegistryBrowserLabelProvider_Fragments;
  public String RegistryBrowserLabelProvider_ImportedPackages;
  public String RegistryBrowserLabelProvider_Properties;
  public String RegistryBrowserLabelProvider_RegisteredBy;
  public String RegistryBrowserLabelProvider_usedServices;
  public String RegistryBrowserLabelProvider_registeredServices;
  public String RegistryBrowserLabelProvider_UsingBundles;

  public static PDERuntimeMessages get() {
    Class clazz = PDERuntimeMessages.class;
    Object result = RWT.NLS.getISO8859_1Encoded( BUNDLE_NAME, clazz );
    return ( PDERuntimeMessages )result;
  }
}
