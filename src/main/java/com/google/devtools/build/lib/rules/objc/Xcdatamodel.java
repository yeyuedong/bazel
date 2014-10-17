// Copyright 2014 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.rules.objc;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.util.FileType;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.build.xcode.util.Value;

/**
 * Represents an .xcdatamodel[d] directory - knowing all {@code Artifact}s contained therein - and
 * the .zip file that it is compiled to which should be merged with the final application bundle.
 * <p>
 * An .xcdatamodel (here and below note that lack or presence of a d) directory contains the schema
 * for a managed object, or a managed object model. It typically has two files: {@code layout} and
 * {@code contents}, although this detail isn't addressed in Bazel code. Directories of this
 * sort are compiled into a single .mom file. If the .xcdatamodel directory is inside a
 * .xcdatamodeld directory, then the .mom file is placed inside a .momd directory. The .momd
 * directory or .mom file is placed in the bundle root of the final bundle.
 * <p>
 * An .xcdatamodeld directory contains several .xcdatamodel directories, each corresponding to a
 * different version. In addition the .xcdatamodeld directory contains a {@code .xccurrentversion}
 * file which identifies the current version. (this file is also not handled explicitly by Bazel
 * code).
 * <p>
 * When processing artifacts referenced by a {@code datamodels} attribute, we must determine if it
 * is in a .xcdatamodeld directory or only a .xcdatamodel directory. We also must group the
 * artifacts by their container, the container being an .xcdatamodeld directory if possible, and a
 * .xcdatamodel directory otherwise. Every container is compiled with a single invocation of the
 * Managed Object Model Compiler (momc) and corresponds to exactly one instance of this class. We
 * invoke momc indirectly through the momczip tool (part of Bazel) which runs momc and zips the
 * output. The files in this zip are placed in the bundle root of the final application, not unlike
 * the zips generated by {@code actooloribtoolzip}.
 */
class Xcdatamodel extends Value<Xcdatamodel> {
  private final Artifact outputZip;
  private final ImmutableSet<Artifact> inputs;
  private final PathFragment container;

  Xcdatamodel(Artifact outputZip, ImmutableSet<Artifact> inputs, PathFragment container) {
    super(ImmutableMap.of(
        "outputZip", outputZip,
        "inputs", inputs,
        "container", container));
    this.outputZip = outputZip;
    this.inputs = inputs;
    this.container = container;
  }

  public Artifact getOutputZip() {
    return outputZip;
  }

  /**
   * Returns every known file in the container. This is every input file that is processed by momc.
   */
  public ImmutableSet<Artifact> getInputs() {
    return inputs;
  }

  public PathFragment getContainer() {
    return container;
  }

  /**
   * The ARCHIVE_ROOT passed to momczip. The archive root is the name of the .mom file 
   * unversioned object models, and the name of the .momd directory for versioned object models.
   */
  public String archiveRootForMomczip() {
    return name() + (container.getBaseName().endsWith(".xcdatamodeld") ? ".momd" : ".mom");
  }

  /**
   * The name of the data model. This is the name of the container without the extension. For
   * instance, if the container is "foo/Information.xcdatamodel" or "bar/Information.xcdatamodeld",
   * then the name is "Information".
   */
  public String name() {
    String baseContainerName = container.getBaseName();
    int lastDot = baseContainerName.lastIndexOf('.');
    return baseContainerName.substring(0, lastDot);
  }

  public static Iterable<Artifact> outputZips(Iterable<Xcdatamodel> models) {
    return Iterables.transform(models, new Function<Xcdatamodel, Artifact>() {
      @Override
      public Artifact apply(Xcdatamodel model) {
        return model.getOutputZip();
      }
    });
  }

  /**
   * Returns a sequence of all unique *.xcdatamodel directories that contain all the artifacts of
   * the given models. Note that this does not return any *.xcdatamodeld directories.
   */
  static Iterable<PathFragment> xcdatamodelDirs(Iterable<Xcdatamodel> models) {
    ImmutableSet.Builder<PathFragment> result = new ImmutableSet.Builder<>();
    for (Xcdatamodel model : models) {
      result.addAll(ObjcCommon.uniqueContainers(model.getInputs(), FileType.of(".xcdatamodel")));
    }
    return result.build();
  }
}
