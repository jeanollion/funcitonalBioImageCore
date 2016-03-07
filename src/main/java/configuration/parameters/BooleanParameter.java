/*
 * Copyright (C) 2015 jollion
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
package configuration.parameters;

import de.caluga.morphium.annotations.Embedded;

/**
 * ChoiceParameter with two elements, 1st = true, 2nd = false
 * @author jollion
 */
public class BooleanParameter extends ChoiceParameter {
    
    public BooleanParameter(String name) {
        this(name, false);
    }
    
    public BooleanParameter(String name, boolean defaultValue) {
        super(name, new String[]{"true", "false"}, defaultValue?"true":"false", false);
    }
    
    public BooleanParameter(String name, String choice1, String choice2, boolean defaultValue) {
        super(name, new String[]{choice1, choice2}, defaultValue?choice1:choice2, false);
        //if (listChoice.length!=2) throw new IllegalArgumentException("List choice should be of length 2");
    }
    
    public boolean getSelected() {
        return this.getSelectedIndex()==0;
    }
    
    public void setSelected(boolean selected){
        if (selected) super.setSelectedIndex(0);
        else super.setSelectedIndex(1);
    }
    
    @Override
    public void setCondValue() {
        if (cond!=null) cond.setActionValue(getSelected());
    }
    
    @Override
    public Boolean getValue() {
        return getSelected();
    }
    
    @Override 
    public void setValue(Object value){
        if (value instanceof Boolean) setSelected((Boolean)value);
        if (value instanceof String) super.setValue(value);
    }
    
}
