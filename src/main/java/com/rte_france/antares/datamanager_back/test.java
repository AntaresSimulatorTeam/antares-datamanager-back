package com.rte_france.antares.datamanager_back;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;


public class test {
    public static final int FIVE_SECONDS = 25000;
    public static final int MAX_Y = 400;
    public static final int MAX_X = 400;


    public static void main(String... args) throws Exception {
       Robot robot = new Robot();
        Random random = new Random();
        while (true) {
            robot.mouseMove(random.nextInt(MAX_X), random.nextInt(MAX_Y));
            Thread.sleep(FIVE_SECONDS);
        }


        // Exemple d'utilisation de la méthode calculerPGCD
        // int a = 36;
        //int b = 24;

        //   int pgcd = calculerPGCD(a, b);
     /*   System.out.println("Le PGCD de " + a + " et " + b + " est : " + pgcd);
        System.out.println(isFoo(null));
        System.out.println(encode("aaabbbcc"));
        System.out.println(computeMultiplN(0));
        System.out.println(isTwin("samir", "samir"));*/
        // List<Double> list = new ArrayList<>();
        //list.add(12D);
        //list.add(30D);
        //list.add(11D);
        //   System.out.println(calculateTotalPrice(list, 50));
     /*   int[] array = new int[]{0,1,2,3,4,5,3};
        System.out.println(calc(array,0,1));
        System.out.println(calc(array,0,5));
        System.out.println(calc(array,0,0));
        System.out.println(calc(array,0,6));


        Map<Integer, String> map = new HashMap<>();
        map.put(3, "fizz");
        map.put(4, "bizz");

        System.out.println(fizzbuzz(5, map));
        System.out.println(fizzbuzz(3, map));
        System.out.println(fizzbuzz(4, map));
        System.out.println(fizzbuzz(12, map));

        System.out.println(javanaise("bonjour"));
        System.out.println(javanaise("ppppp"));
        System.out.println(sumRange(new int[]{1, 20, 3, 10, -2, 100}));
        System.out.println(maxProfit(new int[]{1, 20, 3, 10, -2, 100}));

    */

    }

    public static void printNumber(int n) {
        System.out.println(n);
    }

    public static int calculerPGCD(int a, int b) {
        // Assure que les nombres sont positifs
        a = Math.abs(a);
        b = Math.abs(b);

        // Algorithme d'Euclide pour calculer le PGCD
        while (b != 0) {
            int temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    public static int findLargest(int[] arr) {
        // Vérifie si le tableau est vide
        if (arr == null || arr.length == 0) {
            throw new IllegalArgumentException("Le tableau est vide.");
        }

        // Initialise la variable pour stocker le plus grand élément
        int largest = arr[0];

        // Parcourt le tableau pour trouver le plus grand élément
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > largest) {
                largest = arr[i];
            }
        }

        // Retourne le plus grand élément trouvé
        return largest;
    }

    public static boolean isFoo(String param) {
        return "foo".equals(param);
    }

    public static int computeMultiplN(int n) {
        int somme = 0;
        for (int i = 3; i < n; i++) {
            if (i % 3 == 0 || i % 5 == 0 || i % 7 == 0) {
                somme = somme + i;
            }
        }
        return somme;
    }

    public static String encode(String plainText) {
        char[] chars = plainText.toCharArray();
        StringBuilder result = new StringBuilder();
        int count = 1;
        for (int i = 0; i < chars.length; i++) {
            if (i + 1 < chars.length && chars[i] == chars[i + 1]) {
                count++;
            } else {
                result.append(count);
                result.append(chars[i]);
                count = 1;
            }


        }
        return result.toString();
    }

    public static int[] smallestInterval(int[] array) {
        // Vérifie si le tableau est vide ou null
        if (array == null || array.length < 2) {
            return null;
        }

        // Tri du tableau
        Arrays.sort(array);

        // Initialisation des variables pour conserver l'intervalle le plus petit
        int minDifference = Integer.MAX_VALUE;
        int startIndex = 0;
        int endIndex = 0;

        // Parcours du tableau trié pour trouver la différence minimale entre deux éléments consécutifs
        for (int i = 0; i < array.length - 1; i++) {
            int difference = array[i + 1] - array[i];
            if (difference < minDifference) {
                minDifference = difference;
                startIndex = i;
                endIndex = i + 1;
            }
        }

        // Retourne l'intervalle le plus petit sous forme de tableau {début, fin}
        return new int[]{array[startIndex], array[endIndex]};
    }

    public static boolean isTwin(String a, String b) {
        char[] aChars = a.toCharArray();
        char[] bChars = b.toCharArray();
        boolean isTwin = false;
        if (aChars.length == bChars.length) {
            Arrays.sort(aChars, 0, aChars.length - 1);
            Arrays.sort(bChars, 0, bChars.length - 1);
            for (int i = 0; i < aChars.length - 1; i++) {
                if (aChars[i] == bChars[i]) {
                    isTwin = true;
                }
            }
        } else return false;
        return isTwin;
    }

    public class ChessTournament {
        public static int numberOfPairs(int n) {
            // Calcule n! (factorielle de n)
            long factorialN = factorial(n);

            // Calcule 2! (factorielle de 2)
            long factorial2 = factorial(2);

            // Calcule (n-2)! (factorielle de n-2)
            long factorialNMinus2 = factorial(n - 2);

            // Calcule n! / (2! * (n-2)!) = n*(n-1)/2
            long pairs = factorialN / (factorial2 * factorialNMinus2);

            // Retourne le nombre de paires en tant qu'entier
            return (int) pairs;
        }

        // Fonction pour calculer la factorielle d'un nombre
        private static long factorial(int n) {
            if (n == 0 || n == 1) {
                return 1;
            } else {
                return n * factorial(n - 1);
            }
        }
    }

    public static double calculateTotalPrice(List<Double> prices, int discount) {
        prices.sort(Double::compareTo);
        Double graterPrice = prices.get(prices.size() - 1);
        double makeDiscount = graterPrice - ((graterPrice * discount) / 100);
        prices.remove(prices.size() - 1);
        prices.add(makeDiscount);
        return prices.stream().mapToDouble(Double::doubleValue).sum();

    }

    public static double closestToZero(List<Double> numbers) {
        // Vérifie si la liste est vide
        if (numbers.isEmpty()) {
            throw new IllegalArgumentException("La liste est vide");
        }

        // Initialise la variable qui contiendra le nombre le plus proche de zéro
        double closest = numbers.get(0);

        // Parcourt les nombres dans la liste
        for (double number : numbers) {
            // Vérifie si le nombre actuel est plus proche de zéro que le nombre le plus proche actuel
            if (Math.abs(number) < Math.abs(closest)) {
                closest = number;
            } else if (Math.abs(number) == Math.abs(closest)) {
                // Si les nombres ont la même distance à zéro, on choisit le nombre positif
                closest = Math.max(closest, number);
            }
        }

        return closest;

    }

    public static int calc(int[] array, int a, int b) {
        int somme = 0;
        for (int i = a; i <= b; i++) {
            somme = somme + array[i];
        }
        return somme;
    }

    public static String fizzbuzz(int number, Map<Integer, String> map) {
        StringBuilder result = new StringBuilder();
        map.forEach((integer, s) -> {
            if (number % integer == 0) {
                result.append(s);
            }
        });
        return result.toString().isEmpty() ? String.valueOf(number) : result.toString();
    }

    public static String javanaise(String text) {
        StringBuilder newText = new StringBuilder();
        newText.append(text.charAt(0));
        for (int i = 1; i < text.length(); i++) {
            char c = text.charAt(i);
            char previous = text.charAt(i - 1);
            if ((isVoyelle(c) && !isVoyelle(previous))) {
                newText.append("av");
            }
            newText.append(c);
        }
        return newText.toString();
    }

    private static boolean isVoyelle(char c) {
        return "aouyieAEIUYO".indexOf(c) != -1;
    }

    public static int sumRange(int[] array) {
        int somme = 0;
        for (int j : array) {
            if (j >= 10 && j <= 100) {
                somme += j;
            }
        }
        return somme;
    }

    public static int[] maxProfit(int[] profits) {
        int[] bestMonthProfit = new int[2];
        int indexMaxProfit = 0;
        int maxProfit = profits[0];
        int indexMaxProfitSecond = profits[0];
        for (int i = 0; i < profits.length; i++) {
            if (profits[i] > maxProfit) {
                maxProfit = profits[i];
                indexMaxProfit = i;
            }
        }
        bestMonthProfit[0] = indexMaxProfit;
        for (int i = 0; i < profits.length; i++) {
            if (profits[i] > maxProfit && i != indexMaxProfit) {
                maxProfit = profits[i];
                indexMaxProfitSecond = i;
            }
        }
        bestMonthProfit[1] = indexMaxProfitSecond;
        return bestMonthProfit;
    }


}