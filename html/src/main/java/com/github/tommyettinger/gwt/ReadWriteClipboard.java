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

package com.github.tommyettinger.gwt;

import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtPermissions;
import com.badlogic.gdx.utils.Clipboard;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.History;

/** Basic implementation of clipboard in GWT. Can copy and paste if given permission. */
public class ReadWriteClipboard implements Clipboard {
	private boolean requestedWritePermissions = false;
	private boolean hasWritePermissions = true;

	private final ClipboardWriteHandler writeHandler = new ClipboardWriteHandler();

	private String content = "";

	public ReadWriteClipboard() {
	}

	@Override
	public boolean hasContents () {
		String contents = getContents();
		return contents != null && !contents.isEmpty();
	}

	@Override
	public String getContents () {
		return URL.decodeQueryString(History.getToken());
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

	private native void setContentJSNI (String content) /*-{
		if ("clipboard" in $wnd.navigator) {
			$wnd.navigator.clipboard.writeText(content);
		}
	}-*/;

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
