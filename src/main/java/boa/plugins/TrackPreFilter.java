/*
 * Copyright (C) 2017 jollion
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

import boa.data_structure.StructureObject;
import boa.image.Image;
import boa.image.ImageMask;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 *
 * @author jollion
 */
public interface TrackPreFilter  extends Plugin {
    public void filter(int structureIdx, TreeMap<StructureObject, Image> preFilteredImages, boolean canModifyImages) throws Exception;
    /*public static List<Image> getContinuousImageList(TreeMap<StructureObject, Image> images) {
        
    }*/
    public static Map<Image, ImageMask> getMaskMap(Map<StructureObject, Image> map) {
        return map.entrySet().stream().collect(Collectors.toMap(e->e.getValue(), e->e.getKey().getMask()));
    }
} 