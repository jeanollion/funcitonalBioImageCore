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
package configuration.dataStructure;

import configuration.parameters.Parameter;
import configuration.parameters.SimpleListParameter;
import org.mongodb.morphia.annotations.Embedded;

/**
 *
 * @author jollion
 */
@Embedded
public class StructureList extends SimpleListParameter {

    public StructureList(int unMutableIndex) {
        super("Structures", unMutableIndex);
    }
    
    @Override
    public Structure createChildInstance() {
        return new Structure("new Structure");
    }
    
    public Structure createChildInstance(String name) {
        return new Structure(name);
    }
    
    public String[] getStructuresAsString() {
        String[] res = new String[children.size()];
        int i=0;
        for (Parameter s : children) res[i++] = s.toString();
        return res;
    }
}
