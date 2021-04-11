package ru.igar15.vacancyaggregator.aggregator;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.igar15.vacancyaggregator.model.SkillsReport;
import ru.igar15.vacancyaggregator.util.SkillsReportUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static ru.igar15.vacancyaggregator.aggregator.VacancyConstants.*;

@Component
public class SkillsAggregator implements Aggregator<SkillsReport> {
    private static final String HH_RU_URL_SAMPLE = "https://hh.ru/search/vacancy?text=%s+%s&search_field=name&page=%s";

    @Autowired
    private HtmlDocumentCreator htmlDocumentCreator;

    @Autowired
    private SkillsHtmlParser htmlParser;

    @Override
    public Optional<SkillsReport> getReport(String name, String city, int selection) throws IOException {
        KeySkillsStatistic keySkillsStatistic = aggregateKeySkillsStatistic(name, city, selection);
        if (keySkillsStatistic.vacanciesAmount == 0) {
            return Optional.empty();
        } else {
            return Optional.of(SkillsReportUtil.get(name, city, selection, keySkillsStatistic.vacanciesAmount, keySkillsStatistic.keySkills));
        }
    }

    private KeySkillsStatistic aggregateKeySkillsStatistic(String name, String city, int selection) throws IOException {
        String hhRuUrl = String.format(HH_RU_URL_SAMPLE, name, city, "%s");
        int vacanciesAmount = 0;
        Map<String, Integer> keySkills = new HashMap<>();

        Properties vacancyProperties = new Properties();
        boolean isVacancyPropertiesExist = checkVacancyPropertiesExist(name, vacancyProperties);

        Elements vacancies = null;
        for (int i = 0; i < selection; i++) {
            vacancies = getVacancies(hhRuUrl, i);
            if (vacancies.size() == 0) {
                break;
            }
            for (Element vacancy : vacancies) {
                vacanciesAmount++;
                String vacancyUrl = htmlParser.getVacancyUrl(vacancy);
                Document vacancyPage = htmlDocumentCreator.getDocument(vacancyUrl);
                Elements vacancyKeySkills = htmlParser.getVacancyKeySkills(vacancyPage);
                for (Element vacancyKeySkill : vacancyKeySkills) {
                    String keySkill = vacancyKeySkill.text().toUpperCase();
                    if (keySkill.contains(ENGLISH_KEY_SKILL_PATTERN)) {
                        keySkill = ENGLISH_KEY_SKILL_VALUE;
                    }
                    if (isVacancyPropertiesExist && vacancyProperties.containsKey(keySkill)) {
                        keySkill = vacancyProperties.getProperty(keySkill);
                    }
                    keySkills.merge(keySkill, 1, Integer::sum);
                }
            }
        }
        return new KeySkillsStatistic(vacanciesAmount, keySkills);
    }

    private Elements getVacancies(String url, int pageNumber) throws IOException {
        Document vacanciesPage = null;
        Elements vacancies = null;
        for (int i = 0; i < 10; i++) {
            vacanciesPage = htmlDocumentCreator.getDocument(String.format(url, pageNumber));
            vacancies = htmlParser.getVacancies(vacanciesPage);
            if (vacancies.size() == 0) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                break;
            }
        }
        return vacancies;
    }

    private boolean checkVacancyPropertiesExist(String name, Properties vacancyProperties) {
        try {
            if (name.contains(JAVA) && !name.contains(JAVASCRIPT) && !name.contains(JAVA_SCRIPT)) {
                vacancyProperties.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("vacancies/javaVacancy.properties"));
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private static class KeySkillsStatistic {
        private final int vacanciesAmount;
        private final Map<String, Integer> keySkills;

        public KeySkillsStatistic(int vacanciesAmount, Map<String, Integer> keySkills) {
            this.vacanciesAmount = vacanciesAmount;
            this.keySkills = keySkills;
        }
    }
}