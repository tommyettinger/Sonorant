/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.github.tommyettinger;

import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtPermissions;
import com.badlogic.gdx.utils.Clipboard;

/** Basic implementation of clipboard in GWT. Can copy and paste if given permission. */
public class ReadWriteClipboard implements Clipboard {

	private boolean requestedReadPermissions = false;
	private boolean hasReadPermissions = true;

	private boolean requestedWritePermissions = false;
	private boolean hasWritePermissions = true;

	private final ClipboardReadHandler readHandler = new ClipboardReadHandler();
	private final ClipboardWriteHandler writeHandler = new ClipboardWriteHandler();

	private String content = "";

	@Override
	public boolean hasContents () {
		String contents = getContents();
		return contents != null && !contents.isEmpty();
	}

	@Override
	public String getContents () {
		if (requestedReadPermissions || GwtApplication.agentInfo().isFirefox()) {
			if (hasReadPermissions) content = getContentJSNI();
		} else {
			GwtPermissions.queryPermission("clipboard-read", readHandler);
			requestedReadPermissions = true;
		}
		return content;
	}

	@Override
	public void setContents (String content) {
		this.content = content;
		if (requestedWritePermissions || GwtApplication.agentInfo().isFirefox()) {
			if (hasWritePermissions) setContentJSNI(content);
		} else {
			GwtPermissions.queryPermission("clipboard-write", writeHandler);
			requestedWritePermissions = true;
		}
	}

	private native String getContentJSNI () /*-{
		if ("clipboard" in $wnd.navigator) {
			return $wnd.navigator.clipboard.readText();
		}
		return "";
	}-*/;

	private native void setContentJSNI (String content) /*-{
		if ("clipboard" in $wnd.navigator) {
			$wnd.navigator.clipboard.writeText(content);
		}
	}-*/;

	private class ClipboardReadHandler implements GwtPermissions.GwtPermissionResult {
		@Override
		public void granted () {
			hasReadPermissions = true;
			content = getContentJSNI();
		}

		@Override
		public void denied () {
			hasReadPermissions = false;
		}

		@Override
		public void prompt () {
			hasReadPermissions = true;
			content = getContentJSNI();
		}
	}

	private class ClipboardWriteHandler implements GwtPermissions.GwtPermissionResult {
		@Override
		public void granted () {
			hasWritePermissions = true;
			setContentJSNI(content);
		}

		@Override
		public void denied () {
			hasWritePermissions = false;
		}

		@Override
		public void prompt () {
			hasWritePermissions = true;
			setContentJSNI(content);
		}
	}
}
