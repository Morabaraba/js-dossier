/*
Copyright 2013-2016 Jason Leyba

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.github.jsdossier.soy;

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newBufferedWriter;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import com.github.jsdossier.proto.PageData;
import com.github.jsdossier.proto.Resources;
import com.github.jsdossier.proto.TypeLink;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.SafeUrls;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.proto.SoyProtoTypeProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Provider;

/** Renders soy templates. */
public class Renderer {

  private final Provider<SoyFileSet.Builder> filesetBuilderProvider;
  private final SoyTofu tofu;
  private final JsonRenderer jsonRenderer;

  @Inject
  Renderer(
      Provider<SoyFileSet.Builder> filesetBuilderProvider,
      SoyTypeProvider typeProvider,
      JsonRenderer jsonRenderer) {
    this.filesetBuilderProvider = filesetBuilderProvider;
    this.tofu =
        filesetBuilderProvider
            .get()
            .add(Renderer.class.getResource("resources/types.soy"))
            .add(Renderer.class.getResource("resources/dossier.soy"))
            .setLocalTypeRegistry(new SoyTypeRegistry(ImmutableSet.of(typeProvider)))
            .build()
            .compileToTofu();
    this.jsonRenderer = jsonRenderer;
  }

  public void render(Path htmlOut, Path jsonOut, Resources resources, PageData data)
      throws IOException {
    StringWriter sw = new StringWriter();
    jsonRenderer.render(sw, data);
    String jsonData = sw.toString();

    Files.createDirectories(htmlOut.getParent());
    Files.createDirectories(jsonOut.getParent());

    Files.write(jsonOut, jsonData.getBytes(UTF_8), CREATE, WRITE, TRUNCATE_EXISTING);

    try (Writer writer = newBufferedWriter(htmlOut, UTF_8, CREATE, WRITE, TRUNCATE_EXISTING)) {
      tofu.newRenderer("dossier.soy.page")
          .setData(
              ImmutableMap.of(
                  "resources", resources,
                  "data", data,
                  "jsonData", jsonData,
                  "headContent", renderHeadContent(resources),
                  "tailContent", renderTailContent(resources)))
          .render(writer);
    }
  }

  private SoyValue renderHeadContent(Resources resources) {
    if (resources.getTailScriptList().isEmpty()) {
      return NullData.INSTANCE;
    }
    return renderScripts(resources.getHeadScriptList());
  }

  private SoyValue renderTailContent(Resources resources) {
    if (resources.getTailScriptList().isEmpty()) {
      return NullData.INSTANCE;
    }
    return renderScripts(resources.getTailScriptList());
  }

  private SoyValue renderScripts(List<SafeUrlProto> urls) {
    String template = "{namespace dossier.soy.dynamic}{template .scripts kind=\"html\"}";
    for (SafeUrlProto proto : urls) {
      String url = SafeUrls.fromProto(proto).getSafeUrlString();
      template += "<script src=\"" + url + "\" defer></script>";
    }
    template += "{/template}";

    return filesetBuilderProvider
        .get()
        .add(template, "<dynamic>")
        .build()
        .compileToTofu()
        .newRenderer("dossier.soy.dynamic.scripts")
        .setContentKind(SanitizedContent.ContentKind.HTML)
        .renderStrict();
  }

  public void render(Appendable appendable, String text, TypeLink link, boolean codeLink)
      throws IOException {
    tofu.newRenderer("dossier.soy.type.typeLink")
        .setData(
            ImmutableMap.of(
                "content", text,
                "codeLink", codeLink,
                "href", link.getHref()))
        .render(appendable);
  }

  public static void main(String args[]) throws IOException {
    checkArgument(args.length > 0, "no output directory specified");

    Path outputDir = FileSystems.getDefault().getPath(args[0]);
    checkArgument(Files.isDirectory(outputDir), "not a directory: %s", outputDir);

    Injector injector = Guice.createInjector(new DossierSoyModule());

    SoyTypeProvider typeProvider = injector.getInstance(SoyProtoTypeProvider.class);
    SoyFileSet fileSet =
        injector
            .getInstance(SoyFileSet.Builder.class)
            .add(Renderer.class.getResource("resources/dossier.soy"))
            .add(Renderer.class.getResource("resources/nav.soy"))
            .add(Renderer.class.getResource("resources/types.soy"))
            .setLocalTypeRegistry(new SoyTypeRegistry(ImmutableSet.of(typeProvider)))
            .build();

    SoyJsSrcOptions options = new SoyJsSrcOptions();

    // These options must be disabled before enabling goog modules below.
    options.setShouldDeclareTopLevelNamespaces(false);
    options.setShouldProvideRequireSoyNamespaces(false);
    options.setShouldProvideRequireJsFunctions(false);
    options.setShouldProvideBothSoyNamespacesAndJsFunctions(false);
    options.setShouldGenerateJsdoc(true);

    options.setShouldGenerateGoogModules(true);

    Pattern googModulePattern = Pattern.compile("(goog\\.module\\('.*'\\);)");
    String missingContent =
        "\n/** @suppress {extraRequire} */\n"
            + "goog.require('dossier.soyplugins');\n"
            + "/** @suppress {extraRequire} */\n"
            + "goog.require('goog.soy.data.SanitizedContent');\n";

    Iterator<Path> files =
        ImmutableList.of(
                outputDir.resolve("dossier.soy.js"),
                outputDir.resolve("nav.soy.js"),
                outputDir.resolve("types.soy.js"))
            .iterator();
    for (String string : fileSet.compileToJsSrc(options, null)) {
      Matcher matcher = googModulePattern.matcher(string);
      if (matcher.find()) {
        string = matcher.replaceFirst("$1\n" + missingContent);
      }

      Path file = files.next();
      Files.write(file, string.getBytes(StandardCharsets.UTF_8));
    }
  }
}
