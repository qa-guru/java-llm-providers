# java-llm-providers

Один Java-проект — много провайдеров LLM. Занятие «Как подключить модель»: локальная Ollama, удалённая Ollama, OpenAI-compatible API, free tier, РФ-облака, своя GPU-аренда.

Java 21, Gradle Wrapper, ноль фреймворков — голый `java.net.http` + Jackson.

## Быстрый старт

```bash
git clone https://github.com/qa-guru/java-llm-providers.git
cd java-llm-providers

./gradlew test            # офлайн-тесты, без ключей и сети
./gradlew providers       # кто готов, кто пропущен и почему
./gradlew chat -Dprovider=ollama-local -Dprompt="Привет"
./gradlew bench           # один промпт во всех → таблица ms/tokens/$
```

## Идея

```
providers.properties ──► llm.config.Providers ──► ProviderConfig ──► llm.client.LlmClient
                           (env ${VAR})              ready?            ollama|openai
```

- **`ollama`** — `POST /api/generate`: localhost, удалённая коробка за nginx+basic auth, Ollama на арендованной GPU.
- **`openai`** — `POST /v1/chat/completions`: OpenRouter, Groq, Google AI Studio, DeepSeek, Ollama Cloud, Yandex FM, GigaChat, Selectel FMC. Один клиент — девять провайдеров.

## Структура

```
src/main/java/llm/
  ChatMain.java                    # ./gradlew chat — один промпт в один провайдер
  ProvidersMain.java               # ./gradlew providers — кто готов, кто skip
  bench/BenchMain.java             # ./gradlew bench — все провайдеры × один промпт → таблица
  client/
    LlmClient.java                 # интерфейс «спроси у модели» — публичный API
    LlmResponse.java               # text + tokensIn/tokensOut + durationMs
    LlmException.java              # Kind: TIMEOUT / AUTH / RATE_LIMIT / …
    OllamaClient.java              # POST /api/generate (+ basic auth для удалённой)
    OpenAiCompatibleClient.java    # POST /v1/chat/completions
  config/
    Providers.java                 # loader: properties → configs → client()
    ProviderConfig.java            # один провайдер: url / model / key / прайс
```

Тесты зеркалят пакеты: `llm.bench`, `llm.client`, `llm.config` — офлайн, без сети.

## Конфигурация

Всё — в `src/main/resources/providers.properties`. Секреты — только через `${ENV_VAR}`: файл коммитится, ключи нет.

```properties
provider.groq.type=openai
provider.groq.url=https://api.groq.com/openai
provider.groq.key=${GROQ_API_KEY}
provider.groq.model=llama-3.1-8b-instant
provider.groq.usdInPerM=0.05
provider.groq.usdOutPerM=0.08
```

Цена не приходит из API — декларируем сами (`usd*PerM`), колонка `~$ по прайсу` честная.

## Как взять ключи (бесплатно)

| Провайдер | Где | Что дают |
|-----------|-----|----------|
| OpenRouter | openrouter.ai → Keys | модели `:free`, ~50 req/day |
| Google AI Studio | aistudio.google.com → Get API key | gemini-*-flash-lite, RPM free tier |
| Groq | console.groq.com | free tier, LPU — очень быстро |
| Ollama Cloud | ollama.com → Settings → Keys | cloud-модели бесплатно/подписка |
| GigaChat | developers.sber.ru | freemium 365M токенов/год |
| Yandex FM | console.yandex.cloud → SA → API key | платно, грант на старте |

## Live-смоук одного провайдера

```bash
GROQ_API_KEY=gsk_... ./gradlew test -DincludeTags=live -Dprovider=groq
```

## CI

- PR: `./gradlew test` — офлайн, без секретов.
- `live.yml` (workflow_dispatch): `bench` с секретами репо, артефакт `bench-results.md`.

## Что осознанно НЕ делаем

- Нет Spring/LangChain4j — на паре смотрим в HTTP.
- Нет стриминга — меряем полное время ответа.
- Нет retry/rate-limit-логики — провайдер ответил ошибкой → строка FAIL.
