package io.kestra.plugin.notifications;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.serializers.YamlFlowParser;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;

import java.io.File;
import java.net.URL;

public class InvalidFlowTestHelper {
    @Inject
    YamlFlowParser yamlFlowParser = new YamlFlowParser();

    protected Flow parse(String path) {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return yamlFlowParser.parse(file, Flow.class);
    }
}
