/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.util.decree.handlers;

import com.volmit.iris.Iris;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.object.common.IrisScript;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.decree.DecreeParameterHandler;
import com.volmit.iris.util.decree.exceptions.DecreeParsingException;
import com.volmit.iris.util.decree.exceptions.DecreeWhichException;

import java.io.File;

public class ScriptHandler implements DecreeParameterHandler<IrisScript> {
    @Override
    public KList<IrisScript> getPossibilities() {
        KMap<String, IrisScript> p = new KMap<>();

        //noinspection ConstantConditions
        for (File i : Iris.instance.getDataFolder("packs").listFiles()) {
            if (i.isDirectory()) {
                IrisData data = new IrisData(i, true);
                for (IrisScript j : data.getScriptLoader().loadAll(data.getScriptLoader().getPossibleKeys())) {
                    p.putIfAbsent(j.getLoadKey(), j);
                }

                data.close();
            }
        }

        return p.v();
    }

    @Override
    public String toString(IrisScript script) {
        return script.getLoadKey();
    }

    @Override
    public IrisScript parse(String in) throws DecreeParsingException, DecreeWhichException {
        try {
            KList<IrisScript> options = getPossibilities(in);

            if (options.isEmpty()) {
                throw new DecreeParsingException("Unable to find Script \"" + in + "\"");
            } else if (options.size() > 1) {
                throw new DecreeWhichException();
            }

            return options.get(0);
        } catch (DecreeParsingException e) {
            throw e;
        } catch (Throwable e) {
            throw new DecreeParsingException("Unable to find Script \"" + in + "\" because of an uncaught exception: " + e);
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(IrisScript.class);
    }

    @Override
    public String getRandomDefault() {
        return "script";
    }
}