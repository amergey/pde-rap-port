/**
 * 
 */
package org.eclipse.ui.internal.views.log;

import java.io.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.rwt.RWT;
import org.eclipse.rwt.service.IServiceHandler;

/**
 * {@link IServiceHandler} implementation allowing to download platform log file
 *
 */
public class DownloadServiceHandler implements IServiceHandler {
	private File fInputFile;

	public DownloadServiceHandler(File pInputFile) {
		super();
		fInputFile = pInputFile;
	}

	public DownloadServiceHandler() {
		this(Platform.getLogFileLocation().toFile());
	}

	/**
	 * @see org.eclipse.rwt.service.IServiceHandler#service()
	 */
	public void service() throws IOException, ServletException {
		// Send the file in the response
		HttpServletResponse response = RWT.getResponse();
		response.setContentType("application/octet-stream");
		response.setContentLength((int) fInputFile.length());
		String fileName = fInputFile.getName();
		if (fileName.charAt(0) == '.') {
			fileName = "pde" + fileName;
		}
		String contentDisposition = "attachment; filename=\"" + fileName + '\"';
		response.setHeader("Content-Disposition", contentDisposition);
		InputStream inputStream = null;
		try {
			inputStream = new FileInputStream(fInputFile);
			int b = inputStream.read();
			while (b != -1) {
				response.getOutputStream().write(b);
				b = inputStream.read();
			}

		} catch (IOException e1) {
			MessageDialog.openError(Activator.getDefault().getWorkbench().getActiveWorkbenchWindow().getShell(), Messages.get().LogView_exportLog, e1.toString());
		} finally {
			if (inputStream != null) {
				inputStream.close();
			}
		}

	}

}
