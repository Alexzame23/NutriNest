# Built-in recipe library sources

NutriNest's built-in recipes are original, concise adaptations of familiar
dishes. Recipe names and ingredient ideas were checked against the following
reliable public sources; no source instructions, photographs, or other creative
content are copied into the application.

- [Recipes from Spain — Spain's official tourism portal](https://www.spain.info/en/query/recipes-spain/)
- [Traditional paella method — Spain's official tourism portal](https://www.spain.info/en/recipe/paella/)
- [Food and wine in Italy — Italy's official tourism portal](https://www.italia.it/en/italy/things-to-do/food-and-wine)
- [Traditional pizza technique — Italy's official tourism portal](https://www.italia.it/en/campania/naples/things-to-do/pizza)
- [Italian pasta traditions and preparations — Italia.it](https://www.italia.it/en/italy/things-to-do/pasta-types-italian-formats-and-recipes)
- [Traditional Greek cuisine — Visit Greece](https://www.visitgreece.gr/experiences/gastronomy/traditional-cuisine/)
- [Greek recipe collection — Visit Greece](https://www.visitgreece.gr/en/blog/recipes)
- [Traditional moussaka method — Visit Greece](https://www.visitgreece.gr/el/experiences/gastronomy/recipes/mousaka/)
- [Healthier family recipes — NHS](https://www.nhs.uk/healthier-families/recipes/)
- [Healthier dinner collection — NHS](https://www.nhs.uk/healthier-families/recipes/dinner/)
- [Easy overnight oats method — NHS](https://www.nhs.uk/healthier-families/recipes/easy-overnight-oats/)
- [Fruity French toast method — NHS](https://www.nhs.uk/healthier-families/recipes/fruity-french-toast/)
- [No-pastry mushroom quiche method — NHS](https://www.nhs.uk/healthier-families/recipes/no-pastry-mini-mushroom-quiches/)
- [Healthy eating while trying to lose weight — NHS](https://www.nhs.uk/better-health/lose-weight/healthy-eating-when-trying-to-lose-weight/)
- [USDA MyPlate recipe cookbooks](https://www.myplate.gov/myplate-kitchen/recipes-cookbooks-and-menus)
- [A Harvest of Recipes — USDA](https://www.myplate.gov/sites/default/files/cookbooks/HarvestofRecipes.pdf)
- [Our Regional Cuisines recipe database — Japan MAFF](https://www.maff.go.jp/e/policies/market/k_ryouri/index.html)

## Adaptation and data rules

- The library contains 250 one-serving recipes. Every book has at least 10
  breakfasts, 50 main dishes, and 10 desserts, plus sides and snacks.
- Recipe titles and instructions are stored separately in English and Spanish.
  Step counts follow the actual method: simple assembled dishes stay concise,
  while doughs, layered bakes, paellas, stews, and similar dishes use longer
  methods with explicit heat, timing, texture, resting, and food-safety cues.
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
