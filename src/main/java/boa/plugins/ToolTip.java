/*
 * Copyright (C) 2018 jollion
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package boa.plugins;

import boa.configuration.parameters.Parameter;

/**
 *
 * @author jollion
 */
public interface ToolTip{
    /**
     * The returned tooltip string will be formatted as html in a box with a fixed with, so the \"<html>\" tag is not needed, and line skipping will be automatically managed
     * @return the string that will be displayed as a tool tip when scrolling over the name of the plugin
     */
    public String getToolTipText();
    public static int TOOL_TIP_BOX_WIDTH = 750;
    public static String formatToolTip(String toolTip) {
        if (toolTip.startsWith("<html>")) toolTip = toolTip.replace("<html>", "");
        if (toolTip.endsWith("</html>")) toolTip = toolTip.replace("</html>", "");
        return "<html><div style=\"width:"+TOOL_TIP_BOX_WIDTH+"px\">" + toolTip + "</div></html>";
    }
}
