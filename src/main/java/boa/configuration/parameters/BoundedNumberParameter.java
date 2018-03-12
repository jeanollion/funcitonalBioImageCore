/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.configuration.parameters;


/**
 *
 * @author nasique
 */
public class BoundedNumberParameter extends NumberParameter {
    Number lowerBound, upperBound;
    
    public BoundedNumberParameter(String name, int decimalPlaces, Number defaultValue, Number lowerBound, Number upperBound) {
        super(name, decimalPlaces, defaultValue);
        this.lowerBound=lowerBound;
        this.upperBound=upperBound;
    }

    public Number getLowerBound() {
        return lowerBound;
    }

    public Number getUpperBound() {
        return upperBound;
    }
    
    @Override public BoundedNumberParameter duplicate() {
        return new BoundedNumberParameter(name, decimalPlaces, value, lowerBound, upperBound);
    }
}
