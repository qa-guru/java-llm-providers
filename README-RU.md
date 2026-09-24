# java-llm-providers (RU)

Русская версия — см. README.md: структура и команды одинаковые. Стек: Java 21 + Gradle Wrapper, два типа клиентов (`ollama` и `openai`-совместимый), конфиг в `providers.properties`, секреты через env.

```bash
./gradlew providers   # готовность
./gradlew bench       # таблица «все провайдеры × один промпт»
```
