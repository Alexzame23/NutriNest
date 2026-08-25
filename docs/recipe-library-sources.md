# Built-in recipe library sources

NutriNest's built-in recipes are original, concise adaptations of familiar
dishes. Recipe names and ingredient ideas were checked against the following
reliable public sources; no source instructions, photographs, or other creative
content are copied into the application.

- [Recipes from Spain — Spain's official tourism portal](https://www.spain.info/en/query/recipes-spain/)
- [Food and wine in Italy — Italy's official tourism portal](https://www.italia.it/en/italy/things-to-do/food-and-wine)
- [Traditional Greek cuisine — Visit Greece](https://www.visitgreece.gr/experiences/gastronomy/traditional-cuisine/)
- [Healthier family recipes — NHS](https://www.nhs.uk/healthier-families/recipes/)
- [Healthy eating while trying to lose weight — NHS](https://www.nhs.uk/better-health/lose-weight/healthy-eating-when-trying-to-lose-weight/)

## Adaptation and data rules

- The library contains 72 one-serving recipes: 24 each in Mediterranean
  Cuisine, Low-Calorie Recipes, and World Cuisine.
- Every book covers breakfast, main dishes, sides/appetizers, snacks, and
  desserts.
- Every ingredient is linked by an exact `BEDCA:<food id>` key to the bundled
  BEDCA-derived database. No recipe seeding path creates ingredients.
- Raw database foods are preferred. A prepared BEDCA food is used only when it
  is the closest reliable match (for example, fresh baked cod is preferred to
  raw salted cod).
- The runtime seeder skips and logs recipes with missing mappings and records
  the warning in `food_dataset_meta` under
  `built_in_recipe_library_warnings`.
- Low-calorie recipe quantities were checked against the bundled macro data;
  each complete one-serving recipe is below 600 kcal in the current dataset.
- Recipe and step image paths are left null. This keeps the library fully
  offline without bundling images whose copyright or long-term availability is
  uncertain.
